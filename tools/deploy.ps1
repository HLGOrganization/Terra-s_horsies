<#
.SYNOPSIS
  Deploy the built jar to the instances, refusing to run while the game is up.

.DESCRIPTION
  Why this guard exists: overwriting a mod jar while the game is running crashes it.
  On 2026-10-06 19:17:37 the jar was overwritten, and at 19:18:28 the game crashed with

      NoClassDefFoundError: com/tfcicys/horses/taming/TfcEquineTaming
      Caused by: ClassNotFoundException: com.tfcicys.horses.taming.TfcEquineTaming

  even though that class *was* inside the jar. Forge's ModuleClassLoader builds its
  package -> module index when the game starts; once the file underneath is replaced,
  the index no longer matches the contents and our own classes become unloadable.
  It has nothing to do with the mod being broken. Rule: quit the game, then deploy.

  Step 1: refuse while javaw/java runs (override with -Force, but please do not).
  Step 2: verify key entries are packaged (early warning for packaging mistakes).
  Step 3: copy to the targets and print SHA1 for each one.

  注意：本文件必须保存为 UTF-8 with BOM，否则 Windows PowerShell 5.1 会按 ANSI
  解析，中文会乱码、变量会被吞掉，导致判断失效。
#>
[CmdletBinding()]
param(
    [string[]] $Destination,
    [switch] $Force,
    [switch] $CheckOnly
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$repo = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repo 'build\libs\terras-horsies-1.0.0.jar'

if (-not $Destination -or $Destination.Count -eq 0) {
    $Destination = @(
        (Join-Path $repo 'run\mods'),
        'D:\线框的服务器\versions\TFC_Wild_Fire\mods'
    )
}

if (-not (Test-Path -LiteralPath $source)) {
    Write-Host "构建产物不存在：$source" -ForegroundColor Red
    Write-Host "先构建：.\gradlew.bat build --no-daemon --offline" -ForegroundColor Yellow
    exit 1
}

# ── 1. 游戏在运行就拒绝 ────────────────────────────────────────────
$game = @(Get-Process -Name 'javaw', 'java' -ErrorAction SilentlyContinue)
if ($game.Count -gt 0 -and -not $Force) {
    Write-Host "检测到游戏/服务端正在运行，拒绝部署：" -ForegroundColor Red
    foreach ($p in $game) {
        $mb = [int]($p.WorkingSet64 / 1MB)
        Write-Host ("    PID " + $p.Id + "  " + $p.ProcessName + "  " + $mb + " MB  启动于 " + $p.StartTime) -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "运行中覆盖 mod jar 会让游戏之后加载本模组的类时崩溃：" -ForegroundColor Yellow
    Write-Host "Forge 的包索引在启动时固定，文件被换掉后索引与内容对不上。" -ForegroundColor Yellow
    Write-Host "请先完全退出游戏，再运行本脚本。" -ForegroundColor Yellow
    exit 1
}

# ── 2. 打包完整性 ─────────────────────────────────────────────────
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
    Write-Host "jar 缺少关键条目，打包有问题：" -ForegroundColor Red
    foreach ($m in $missing) { Write-Host "    $m" -ForegroundColor Red }
    exit 1
}

$hash = (Get-FileHash -LiteralPath $source -Algorithm SHA1).Hash.ToLower()
$size = (Get-Item -LiteralPath $source).Length
Write-Host ("构建产物  " + $size + " B  sha1=" + $hash) -ForegroundColor Green
Write-Host "关键条目齐全，游戏未在运行。" -ForegroundColor Green

if ($CheckOnly) { exit 0 }

# ── 3. 部署 ───────────────────────────────────────────────────────
foreach ($dir in $Destination) {
    if (-not (Test-Path -LiteralPath $dir)) {
        Write-Host ("目标目录不存在，跳过：" + $dir) -ForegroundColor Yellow
        continue
    }
    $target = Join-Path $dir 'terras-horsies-1.0.0.jar'
    Copy-Item -LiteralPath $source -Destination $target -Force
    $tHash = (Get-FileHash -LiteralPath $target -Algorithm SHA1).Hash.ToLower()
    if ($tHash -eq $hash) {
        Write-Host ("  OK  " + $target + "  sha1=" + $tHash) -ForegroundColor Green
    } else {
        Write-Host ("  不一致  " + $target + "  sha1=" + $tHash) -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "部署完成，现在可以启动游戏了。" -ForegroundColor Green
