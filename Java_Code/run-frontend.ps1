# Starts the CardDemo Angular frontend on http://localhost:4200
#
# The backend must be running on http://localhost:8080; change
# Frontend/src/environments/environment.ts if it listens elsewhere.

$ErrorActionPreference = 'Stop'

Push-Location (Join-Path $PSScriptRoot 'Frontend')
try {
    if (-not (Test-Path 'node_modules')) {
        Write-Host 'Installing npm dependencies ...'
        npm install
    }
    Write-Host 'Starting CardDemo frontend on http://localhost:4200'
    npm start
}
finally {
    Pop-Location
}
