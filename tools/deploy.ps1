# deploy.ps1 -- copy the built jar to the instances, refusing while the game runs.
#
# WHY THIS EXISTS
#   Overwriting a mod jar while the game is running crashes it. On 2026-10-06 the jar
#   was overwritten at 19:17:37 while the game was already up, and at 19:18:28 the game
#   crashed with:
#       NoClassDefFoundError: com/tfcicys/horses/taming/TfcEquineTaming
#       Caused by: ClassNotFoundException: com/tfcicys/horses/taming/TfcEquineTaming
#   even though that class WAS inside the jar. Forge's ModuleClassLoader builds its
#   package -> module index when the game starts; once the file underneath is replaced,
#   the index no longer matches the contents and our own classes become unloadable.
#   Nothing to do with the mod being broken -- any jar swap does this.
#   Rule: quit the game completely, then deploy.
#
#   Steps: (1) refuse while javaw/java runs, (2) verify key entries are packaged,
#          (3) copy to the targets and print SHA1 for each.
#
# KEEP THIS FILE ASCII-ONLY. Windows PowerShell 5.1 parses a BOM-less file as ANSI, so
# any non-ASCII character here corrupts the parse (Chinese messages silently broke an
# earlier revision of this script). Chinese notes live in docs/DEV_NOTES.md instead.

[CmdletBinding()]
param(
    [string[]] $Destination,
    [switch] $Force,
    [switch] $CheckOnly
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repo 'build\libs\terras-horsies-1.0.0.jar'

if (-not $Destination -or $Destination.Count -eq 0) {
    $Destination = @(Join-Path $repo 'run\mods')
    # The server instance directory contains non-ASCII characters. Rather than putting
    # them in this file (see the ASCII-only note above), resolve it with a wildcard.
    $Destination += @(Get-ChildItem -Path 'D:\*\versions\TFC_Wild_Fire\mods' -Directory -ErrorAction SilentlyContinue |
                      ForEach-Object { $_.FullName })
}

if (-not (Test-Path -LiteralPath $source)) {
    Write-Host "[deploy] missing artifact: $source" -ForegroundColor Red
    Write-Host "[deploy] build first: .\gradlew.bat build --no-daemon --offline" -ForegroundColor Yellow
    exit 1
}

# --- 1. refuse while the game is running ---------------------------------------
# The trailing "| Where-Object { $_ }" matters: when nothing matches, Get-Process can
# yield a single $null, and @($null).Count is 1 (not 0), which would look like "running".
$game = @(Get-Process -Name 'javaw', 'java' -ErrorAction SilentlyContinue | Where-Object { $_ })
if ($game.Count -gt 0 -and -not $Force) {
    Write-Host "[deploy] REFUSED: a Java process is running:" -ForegroundColor Red
    foreach ($p in $game) {
        $mb = [int]($p.WorkingSet64 / 1MB)
        Write-Host ("[deploy]     PID " + $p.Id + "  " + $p.ProcessName + "  " + $mb + " MB  started " + $p.StartTime) -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "[deploy] Overwriting a mod jar under a running game makes it crash later," -ForegroundColor Yellow
    Write-Host "[deploy] when it tries to load our classes (stale Forge package index)." -ForegroundColor Yellow
    Write-Host "[deploy] Quit the game completely, then run this script again." -ForegroundColor Yellow
    exit 1
}

# --- 2. packaging sanity ------------------------------------------------------
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($source)
try {
    $names = @($zip.Entries | ForEach-Object { $_.FullName })
} finally {
    $zip.Dispose()
}

$required = @(
    'com/tfcicys/horses/taming/TfcEquineTaming.class',
    'com/tfcicys/horses/load/LoadManager.class',
    'com/tfcicys/horses/mixin/AbstractHorseInventoryAccess.class',
    'terras_horsies.mixins.json',
    'terras_horsies.refmap.json'
)
$missing = @($required | Where-Object { $names -notcontains $_ })
if ($missing.Count -gt 0) {
    Write-Host "[deploy] jar is missing key entries:" -ForegroundColor Red
    foreach ($m in $missing) { Write-Host "[deploy]     $m" -ForegroundColor Red }
    exit 1
}

$hash = (Get-FileHash -LiteralPath $source -Algorithm SHA1).Hash.ToLower()
$size = (Get-Item -LiteralPath $source).Length
Write-Host ("[deploy] artifact " + $size + " B  sha1=" + $hash) -ForegroundColor Green
Write-Host "[deploy] key entries present; no Java process running." -ForegroundColor Green

if ($CheckOnly) { exit 0 }

# --- 3. deploy ----------------------------------------------------------------
foreach ($dir in $Destination) {
    if (-not (Test-Path -LiteralPath $dir)) {
        Write-Host ("[deploy] skip (no such directory): " + $dir) -ForegroundColor Yellow
        continue
    }
    $target = Join-Path $dir 'terras-horsies-1.0.0.jar'
    Copy-Item -LiteralPath $source -Destination $target -Force
    $tHash = (Get-FileHash -LiteralPath $target -Algorithm SHA1).Hash.ToLower()
    if ($tHash -eq $hash) {
        Write-Host ("[deploy]   OK  " + $target + "  sha1=" + $tHash) -ForegroundColor Green
    } else {
        Write-Host ("[deploy]   MISMATCH  " + $target + "  sha1=" + $tHash) -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "[deploy] done; the game may be started now." -ForegroundColor Green
