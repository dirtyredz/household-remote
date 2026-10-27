<#
.SYNOPSIS
Sign both locally built flavors with disposable keys; never contacts 1Password or a TV.
.DESCRIPTION
Foreground offline smoke. Exit 0 success/dry-run, 1 failure. All temporary files stay in this worktree.
#>
[CmdletBinding()]
param([switch]$DryRun, [string]$AndroidSdk = $env:ANDROID_HOME)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../owner/owner-run.ps1')
Initialize-OwnerRun 'signing-smoke'
try {
    if ($DryRun) { Write-Log 'DRY RUN: disposable keys, both APK/AAB flavors, signature/package verification and wrong-key rejection.'; Complete-OwnerRun 0 }
    if (-not $AndroidSdk) { $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $env:ANDROID_HOME = $AndroidSdk
    $code = Invoke-Native (Find-Tool 'node') @((Join-Path $script:RepoRoot 'src/android/releases/signing-smoke.mjs'))
    if ($code -ne 0) { throw 'Disposable signing smoke failed' }
} catch { Complete-OwnerRun 1 $_.Exception.Message }
Complete-OwnerRun 0
