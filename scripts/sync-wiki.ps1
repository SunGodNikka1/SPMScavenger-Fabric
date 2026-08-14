[CmdletBinding(DefaultParameterSetName = 'Check')]
param(
    [Parameter(ParameterSetName = 'Check')]
    [switch]$Check,

    [Parameter(Mandatory = $true, ParameterSetName = 'Sync')]
    [switch]$Sync,

    [string]$WikiRemote = 'https://github.com/SunGodNikka1/SPMScavenger-Fabric.wiki.git'
)

$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$localWiki = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'docs/wiki'))
$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$tempWiki = Join-Path $tempBase ('spmscavenger-wiki-sync-' + [guid]::NewGuid().ToString('N'))
$mode = if ($Sync) { 'Sync' } else { 'Check' }

function Get-WikiFiles([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return @()
    }

    return @(Get-ChildItem -LiteralPath $Path -File -Filter '*.md' | Sort-Object Name)
}

function Get-NormalizedText([string]$Path) {
    $text = Get-Content -Raw -LiteralPath $Path
    return $text.Replace("`r`n", "`n").Replace("`r", "`n")
}

try {
    & git clone --quiet --depth 1 $WikiRemote $tempWiki
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to clone authoritative Wiki repository: $WikiRemote"
    }

    $wikiRevision = (& git -C $tempWiki rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to resolve the authoritative Wiki revision.'
    }

    $remoteFiles = Get-WikiFiles $tempWiki
    $localFiles = Get-WikiFiles $localWiki
    $remoteByName = @{}
    $localByName = @{}
    foreach ($file in $remoteFiles) { $remoteByName[$file.Name] = $file }
    foreach ($file in $localFiles) { $localByName[$file.Name] = $file }

    $allNames = @($remoteByName.Keys + $localByName.Keys | Sort-Object -Unique)
    $drift = [System.Collections.Generic.List[string]]::new()
    foreach ($name in $allNames) {
        if (-not $remoteByName.ContainsKey($name)) {
            $drift.Add("extra local page: $name")
            continue
        }
        if (-not $localByName.ContainsKey($name)) {
            $drift.Add("missing local page: $name")
            continue
        }

        $remoteText = Get-NormalizedText $remoteByName[$name].FullName
        $localText = Get-NormalizedText $localByName[$name].FullName
        if ($remoteText -cne $localText) {
            $drift.Add("content differs: $name")
        }
    }

    if ($mode -eq 'Check') {
        if ($drift.Count -gt 0) {
            Write-Error ("docs/wiki differs from Wiki revision {0}:`n - {1}`nRun: pwsh ./scripts/sync-wiki.ps1 -Sync" -f $wikiRevision, ($drift -join "`n - "))
            exit 1
        }

        Write-Host "docs/wiki matches authoritative Wiki revision $wikiRevision"
        exit 0
    }

    New-Item -ItemType Directory -Force -Path $localWiki | Out-Null
    foreach ($file in $remoteFiles) {
        Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $localWiki $file.Name) -Force
    }

    foreach ($file in $localFiles) {
        if (-not $remoteByName.ContainsKey($file.Name)) {
            $resolvedFile = [System.IO.Path]::GetFullPath($file.FullName)
            $wikiPrefix = $localWiki.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
            if (-not $resolvedFile.StartsWith($wikiPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to remove a file outside docs/wiki: $resolvedFile"
            }
            Remove-Item -LiteralPath $resolvedFile -Force
        }
    }

    Write-Host "Synchronized docs/wiki from authoritative Wiki revision $wikiRevision"
}
finally {
    if (Test-Path -LiteralPath $tempWiki) {
        $resolvedTemp = [System.IO.Path]::GetFullPath($tempWiki)
        $safePrefix = Join-Path $tempBase 'spmscavenger-wiki-sync-'
        if ($resolvedTemp.StartsWith($safePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
        }
    }
}
