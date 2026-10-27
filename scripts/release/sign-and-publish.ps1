<#
.SYNOPSIS
Verify a successful private CI release, sign locally using 1Password, publish public assets last.
.DESCRIPTION
Owner-run, attended. Requires installed gh, op, Node, Java and Android build tools 35.0.0.
Dry run does not access 1Password or GitHub. Exit 0 success/dry-run, 1 failure.
#>
[CmdletBinding()]
param([switch]$DryRun, [string]$Tag, [string]$RunId, [string]$KeyDocument, [string]$PasswordReference, [string]$Vault = 'Personal', [string]$AndroidSdk = $env:ANDROID_HOME)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../owner/owner-run.ps1')
Initialize-OwnerRun 'sign-and-publish'
$key = $null
try {
    if ($DryRun) { Write-Log 'DRY RUN: verify private CI run/tag/artifact provenance; retrieve key; sign both APK/AAB flavors; verify signer/packages/bytes; create public draft; download/compare; publish last.'; Complete-OwnerRun 0 }
    if ($Tag -notmatch '^v\d+\.\d+\.\d+$' -or $RunId -notmatch '^\d+$' -or -not $KeyDocument -or -not $PasswordReference) { throw 'Provide Tag, RunId, KeyDocument and PasswordReference' }
    foreach ($tool in @('gh', 'op', 'node', 'keytool', 'jarsigner')) { if (-not (Find-Tool $tool)) { throw "$tool required" } }
    $key = Join-Path $script:RepoRoot ('.tmp/attended-' + [guid]::NewGuid() + '.p12')
    $env:HOUSEHOLD_SIGN_PASSWORD = (& op read $PasswordReference).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $env:HOUSEHOLD_SIGN_PASSWORD) { throw 'Cannot read signing password' }
    Add-LogSecret $env:HOUSEHOLD_SIGN_PASSWORD
    & op document get $KeyDocument --vault $Vault --out-file $key
    if ($LASTEXITCODE -ne 0) { throw 'Cannot retrieve signing document' }
    $env:HOUSEHOLD_KEY_FILE = $key
    $env:ANDROID_HOME = $AndroidSdk
    $code = Invoke-Native (Find-Tool 'node') @((Join-Path $script:RepoRoot 'src/android/releases/publish.mjs'), $Tag, $RunId)
    if ($code -ne 0) { throw 'Release verification/signing/publication failed; inspect log; any public draft remains unpublished' }
} catch { if ($key -and (Test-Path -LiteralPath $key)) { Remove-Item -LiteralPath $key -Force }; $env:HOUSEHOLD_SIGN_PASSWORD = $null; $env:HOUSEHOLD_KEY_FILE = $null; Complete-OwnerRun 1 $_.Exception.Message }
finally { if ($key -and (Test-Path -LiteralPath $key)) { Remove-Item -LiteralPath $key -Force }; $env:HOUSEHOLD_SIGN_PASSWORD = $null; $env:HOUSEHOLD_KEY_FILE = $null }
Complete-OwnerRun 0
