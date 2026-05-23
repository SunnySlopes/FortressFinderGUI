# Configure CMake with MinGW. Run from project root:
#   powershell -ExecutionPolicy Bypass -File scripts/configure-mingw.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$gccCandidates = @(
    "$env:MINGW_PREFIX\bin\gcc.exe",
    "C:\msys64\mingw64\bin\gcc.exe",
    "C:\mingw64\bin\gcc.exe",
    "C:\Program Files\mingw-w64\*\mingw64\bin\gcc.exe"
)

$gcc = $null
foreach ($pattern in $gccCandidates) {
    $found = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        $gcc = $found.FullName
        break
    }
}

if (-not $gcc) {
    $gcc = (Get-Command gcc -ErrorAction SilentlyContinue).Source
}

if (-not $gcc) {
    Write-Host @"

ERROR: No MinGW gcc found.

CMake picked 'NMake Makefiles' because no C++ compiler is in PATH, but nmake/Visual Studio is not installed.

Install one of:
  1. MSYS2: https://www.msys2.org/
     Then in MSYS2 UCRT64 terminal: pacman -S mingw-w64-ucrt-x86_64-gcc mingw-w64-ucrt-x86_64-cmake make
     Add to PATH: C:\msys64\ucrt64\bin

  2. Or Visual Studio 2022 with "Desktop development with C++" workload, then use:
     cmake -B build -G "Visual Studio 17 2022" -A x64

After installing MinGW, reopen PowerShell and run this script again.

"@ -ForegroundColor Red
    exit 1
}

$bin = Split-Path $gcc -Parent
$gpp = Join-Path $bin "g++.exe"
if (-not (Test-Path $gpp)) {
    Write-Host "ERROR: g++ not found next to gcc at $bin" -ForegroundColor Red
    exit 1
}

Write-Host "Using gcc: $gcc"
Write-Host "Using g++: $gpp"

if (Test-Path "build\CMakeCache.txt") {
    Remove-Item -Recurse -Force build
}

cmake -B build -G "MinGW Makefiles" `
    -DCMAKE_BUILD_TYPE=Release `
    -DCMAKE_C_COMPILER="$gcc" `
    -DCMAKE_CXX_COMPILER="$gpp"

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Configure OK. Build with:" -ForegroundColor Green
Write-Host "  cmake --build build --target fortressFinderLibJ"
