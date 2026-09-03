param(
    [switch]$Clean = $false
)

# Script compile toan bo cac service trong he thong
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  COMPILING ALL MICROSERVICES (NO TESTS) " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Set JAVA_HOME neu chua set hoac dang tro vao ban JDK cu
if (-not $env:JAVA_HOME -or (Test-Path "C:\Program Files\Java\jdk-21")) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
}

$rootPath = $PSScriptRoot
if (-not $rootPath) {
    $rootPath = Get-Location
}

Push-Location $rootPath

if ($Clean) {
    Write-Host "Cleaning previous build artifacts..." -ForegroundColor Yellow
    ./gradlew clean
}

Write-Host "Compiling Java classes..." -ForegroundColor Yellow
./gradlew compileJava -x test

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n=========================================" -ForegroundColor Red
    Write-Host "  COMPILE FAILED!" -ForegroundColor Red
    Write-Host "=========================================" -ForegroundColor Red
    Pop-Location
    exit 1
}

Pop-Location

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host "  ALL MICROSERVICES COMPILED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
