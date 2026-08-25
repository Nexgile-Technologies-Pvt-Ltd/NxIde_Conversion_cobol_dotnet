# Starts the CardDemo Spring Boot backend on http://localhost:8080
#
# Secrets are read from Backend/.env (gitignored). Copy Backend/.env.example to
# Backend/.env and fill it in before the first run. Any variable already set in the
# environment wins, so a deployment can supply them from its own secret store instead.

$ErrorActionPreference = 'Stop'

$backend = Join-Path $PSScriptRoot 'Backend'

# ------------------------------------------------------------------ secrets
$envFile = Join-Path $backend '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $name, $value = $line -split '=', 2
            $name = $name.Trim()
            if ($name -and -not [Environment]::GetEnvironmentVariable($name)) {
                Set-Item -Path "Env:$name" -Value $value.Trim()
            }
        }
    }
    Write-Host "Loaded secrets from Backend/.env"
}

foreach ($required in 'DB_PASSWORD', 'CARDDEMO_JWT_SECRET') {
    if (-not [Environment]::GetEnvironmentVariable($required)) {
        throw "$required is not set. Copy Backend/.env.example to Backend/.env and fill it in, or export $required."
    }
}

# ------------------------------------------------------------------ toolchain
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem 'C:\Program Files\Microsoft\jdk-21*' -Directory -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME is not set and no JDK 21 was found. Install JDK 21 and set JAVA_HOME.'
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $bundled = 'C:\Users\Administrator\tools\apache-maven-3.9.9\bin'
    if (Test-Path $bundled) { $env:PATH = "$bundled;$env:PATH" }
}

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "JAVA_HOME : $env:JAVA_HOME"
Write-Host 'Starting CardDemo backend on http://localhost:8080 (Swagger at /swagger-ui.html)'

Push-Location $backend
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
