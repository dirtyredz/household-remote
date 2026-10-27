<#
.SYNOPSIS
Create the public releases-only repository during an attended owner run.
.DESCRIPTION
Never changes source repository visibility. Requires authenticated gh. No downloads.
Exit 0 success/dry-run, 1 failure. Existing repositories must already be public.
#>
[CmdletBinding()]
param([switch]$DryRun)
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../owner/owner-run.ps1')
Initialize-OwnerRun 'setup-releases'
try {
    if ($DryRun) { Write-Log 'DRY RUN: create or verify public dirtyredz/household-remote-releases; source stays private.'; Complete-OwnerRun 0 }
    $gh = Find-Tool 'gh'
    if (-not $gh) { throw 'Authenticated gh is required' }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $result = & $gh repo view dirtyredz/household-remote-releases --json visibility 2>$null
    $exists = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previous
    if ($exists) {
        if (($result | ConvertFrom-Json).visibility -ne 'PUBLIC') { throw 'Existing releases repository is not public; inspect it before changing visibility' }
    } else {
        if ((Invoke-Native $gh @('repo','create','dirtyredz/household-remote-releases','--public','--description','Signed Household Remote Android release assets only; source is private.')) -ne 0) { throw 'Could not create public releases repository' }
    }
    Write-Log 'Public releases repository ready. No source, TV inventory or signing material uploaded.'
} catch { Complete-OwnerRun 1 $_.Exception.Message }
Complete-OwnerRun 0
