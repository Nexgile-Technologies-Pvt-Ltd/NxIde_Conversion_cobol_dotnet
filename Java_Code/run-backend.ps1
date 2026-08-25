# Starts the CardDemo Spring Boot backend on http://localhost:8080
#
# Set JAVA_HOME to a JDK 21 installation and put Maven on PATH before running, or override the
# two variables below.

$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem 'C:\Program Files\Microsoft\jdk-21*' -Directory -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME is not set and no JDK 21 was found. Install JDK 21 and set JAVA_HOME.'
}

$maven = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $maven) {
    $bundled = 'C:\Users\Administrator\tools\apache-maven-3.9.9\bin'
    if (Test-Path $bundled) { $env:PATH = "$bundled;$env:PATH" }
}

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "JAVA_HOME : $env:JAVA_HOME"
Write-Host 'Starting CardDemo backend on http://localhost:8080 (Swagger at /swagger-ui.html)'

Push-Location (Join-Path $PSScriptRoot 'Backend')
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
