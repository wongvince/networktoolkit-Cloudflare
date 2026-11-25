$ErrorActionPreference = 'Stop'

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
  throw 'Gradle 8.10.2+ was not found. Install Gradle and JDK 17, then retry.'
}

gradle packageReleaseExe createDistributable
if ($LASTEXITCODE -ne 0) {
  throw "Gradle build failed with exit code $LASTEXITCODE"
}

Write-Host 'Build complete. Check build\compose\binaries\main-release\exe and build\compose\binaries\main\app.' -ForegroundColor Green
