# Generate JNI C header from FortressFinderBridge.java
# Usage (from project root):
#   powershell -ExecutionPolicy Bypass -File scripts/generate-jni-header.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$javaSrc = "src\main\java\sunnyslopes\fortressfinder\FortressFinderBridge.java"
$outDir = "jni\generated"

if (-not (Test-Path $javaSrc)) {
    Write-Host "ERROR: Missing $javaSrc" -ForegroundColor Red
    exit 1
}

$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javac) {
    Write-Host "ERROR: javac not found. Install JDK and add bin to PATH." -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# Remove stale header so missing generation fails loudly
$header = Join-Path $outDir "sunnyslopes_fortressfinder_FortressFinderBridge.h"
if (Test-Path $header) { Remove-Item $header -Force }

& javac -encoding UTF-8 -h $outDir $javaSrc
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not (Test-Path $header)) {
    Write-Host "ERROR: javac did not produce $header" -ForegroundColor Red
    exit 1
}

Write-Host "Generated: $header" -ForegroundColor Green
Write-Host "C++ bridge should include: generated/sunnyslopes_fortressfinder_FortressFinderBridge.h"
