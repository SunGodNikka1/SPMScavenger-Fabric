# Slice 4A — use an existing instance; do NOT duplicate mods into Gradle run/mods.
#
# Default instance (user modpack):
#   D:\Minecraft\Instances\Fabulously Optimized
#
# Usage:
#   .\scripts\setup-perf-run.ps1
#   .\scripts\setup-perf-run.ps1 -ModpackRoot "D:\Minecraft\Instances\Fabulously Optimized"
#   .\scripts\setup-perf-run.ps1 -UnlinkDatapack
param(
    [string]$ModpackRoot = 'D:\Minecraft\Instances\Fabulously Optimized',
    [switch]$UnlinkDatapack
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$SrcDatapack = Join-Path $ProjectRoot 'test-datapacks\phase4-perf'
$DestDatapack = Join-Path $ModpackRoot 'datapacks\phase4-perf'

if (-not (Test-Path $ModpackRoot)) {
    Write-Error "Modpack root not found: $ModpackRoot"
}

if ($UnlinkDatapack) {
    if (Test-Path $DestDatapack) {
        Remove-Item -LiteralPath $DestDatapack -Force -Recurse
        Write-Host "Removed datapack link: $DestDatapack"
    }
    exit 0
}

New-Item -ItemType Directory -Force -Path (Split-Path $DestDatapack) | Out-Null

if (Test-Path $DestDatapack) {
    Write-Host "Datapack already present: $DestDatapack"
} else {
    New-Item -ItemType Junction -Path $DestDatapack -Target $SrcDatapack | Out-Null
    Write-Host "Linked datapack: $DestDatapack -> $SrcDatapack"
}

Write-Host ""
Write-Host "Modpack: $ModpackRoot"
Write-Host "Do NOT copy SPM or Spark — use mods already in the instance."
Write-Host "Scavenger under test: copy ONLY if missing:"
Write-Host "  build\libs\spmscavenger-*.jar -> $ModpackRoot\mods\"
Write-Host ""
Write-Host "In-game (flat test world, enable datapack phase4-perf):"
Write-Host "  /function spm_phase4:setup"
Write-Host "  /function spm_phase4:anchor/set"
Write-Host "  /function spm_phase4:profile/p4a_representative/run"
Write-Host "  warm 60s -> /spark profiler start --timeout 120"
