<#
.SYNOPSIS
Create this app's permanent signing identity in an attended 1Password session.
.DESCRIPTION
Owner-only. Uses installed keytool/op, no downloads. Writes only repo .tmp and public fingerprint.
Exit 0 success/dry-run, 1 failure. Run once, then commit the public fingerprint before release.
#>
[CmdletBinding()]
param([switch]$DryRun, [string]$Vault = 'Personal')
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../owner/owner-run.ps1')
Initialize-OwnerRun 'provision-signing'
$key = $null
try {
    if ($DryRun) { Write-Log 'DRY RUN: create PKCS12 key, store password and document in 1Password, record public certificate fingerprint.'; Complete-OwnerRun 0 }
    $record = Join-Path $script:RepoRoot 'src/android/releases/signing-certificate.json'
    if ((Get-Content -Raw $record | ConvertFrom-Json).certificateSha256) { throw 'Signing identity already provisioned; refusing replacement.' }
    foreach ($tool in @('keytool', 'op')) { if (-not (Find-Tool $tool)) { throw "$tool is required" } }
    $key = Join-Path $script:RepoRoot ('.tmp/signing-' + [guid]::NewGuid() + '.p12')
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes); $rng.Dispose()
    $env:HOUSEHOLD_SIGN_PASSWORD = [Convert]::ToBase64String($bytes)
    Add-LogSecret $env:HOUSEHOLD_SIGN_PASSWORD
    if ((Invoke-Native (Find-Tool 'keytool') @('-genkeypair','-keystore',$key,'-storetype','PKCS12','-alias','household','-keyalg','RSA','-keysize','4096','-validity','10000','-dname','CN=Household Remote','-storepass:env','HOUSEHOLD_SIGN_PASSWORD','-keypass:env','HOUSEHOLD_SIGN_PASSWORD')) -ne 0) { throw 'Key generation failed' }
    $item = @{ title = 'Household Remote signing password'; category = 'PASSWORD'; fields = @(@{ id = 'password'; type = 'CONCEALED'; purpose = 'PASSWORD'; value = $env:HOUSEHOLD_SIGN_PASSWORD }) } | ConvertTo-Json -Depth 5
    $created = $item | & op item create --vault $Vault --format json -
    if ($LASTEXITCODE -ne 0) { throw 'Password storage failed; keep this run attended' }
    $passwordId = ($created | ConvertFrom-Json).id
    $document = & op document create $key --title 'Household Remote signing key' --vault $Vault --format json
    if ($LASTEXITCODE -ne 0) { throw 'Key document storage failed; password item exists, but no usable signing identity was recorded' }
    $documentId = ($document | ConvertFrom-Json).id
    $cert = New-Object Security.Cryptography.X509Certificates.X509Certificate2($key, $env:HOUSEHOLD_SIGN_PASSWORD, [Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet)
    $sha = [Security.Cryptography.SHA256]::Create()
    $fingerprint = ([BitConverter]::ToString($sha.ComputeHash($cert.RawData))).Replace('-', '').ToLowerInvariant()
    $cert.Dispose(); $sha.Dispose()
    @{ certificateSha256 = $fingerprint; status = 'Provisioned; key held in 1Password' } | ConvertTo-Json | Set-Content -Encoding UTF8 $record
    Write-Log 'Provisioned. Retrieve the document and password references in 1Password for the attended signing run. Keep them in private owner records. Commit only the public fingerprint.'
} catch { if ($key -and (Test-Path -LiteralPath $key)) { Remove-Item -LiteralPath $key -Force }; $env:HOUSEHOLD_SIGN_PASSWORD = $null; Complete-OwnerRun 1 $_.Exception.Message }
finally { if ($key -and (Test-Path -LiteralPath $key)) { Remove-Item -LiteralPath $key -Force }; $env:HOUSEHOLD_SIGN_PASSWORD = $null }
Complete-OwnerRun 0
