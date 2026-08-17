[CmdletBinding()]
param([int]$TimeoutSeconds = 240)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
if (-not (Test-Path '.env')) { throw 'Crie .env a partir de .env.example e defina senhas locais.' }
if ((Get-Content '.env' -Raw) -match 'replace-with-a-local') { throw '.env ainda contem placeholders.' }

$projectName = "credit-flow-e2e-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$env:COMPOSE_PROJECT_NAME = $projectName
try {
    docker compose down --volumes --remove-orphans | Out-Host
    .\gradlew.bat bootJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'bootJar failed.' }
    docker compose up --build --detach --wait --wait-timeout $TimeoutSeconds --renew-anon-volumes | Out-Host
    $env:E2E_COMPOSE_PROJECT = $projectName
    node --test --test-concurrency=1 --test-reporter=tap test/e2e/credit-flow.spec.mjs
    if ($LASTEXITCODE -ne 0) { throw "E2E tests failed with exit code $LASTEXITCODE." }
}
finally {
    docker compose down --volumes --remove-orphans | Out-Host
    Remove-Item Env:COMPOSE_PROJECT_NAME -ErrorAction SilentlyContinue
    Remove-Item Env:E2E_COMPOSE_PROJECT -ErrorAction SilentlyContinue
}
