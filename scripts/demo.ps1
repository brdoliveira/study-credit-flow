[CmdletBinding()]
param(
    [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Test-Path '.env')) {
    throw 'Crie .env a partir de .env.example e defina senhas locais antes da demonstração.'
}

$environmentFile = Get-Content '.env' -Raw
if ($environmentFile -match 'replace-with-a-local') {
    throw '.env ainda contém placeholders. Defina senhas locais antes da demonstração.'
}

docker compose ps
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível consultar o Docker Compose.' }

$readiness = Invoke-RestMethod 'http://localhost:8080/actuator/health/readiness'
if ($readiness.status -ne 'UP') { throw 'A readiness da aplicação não está UP. Execute docker compose logs app keycloak postgres kafka.' }

Write-Host 'Ambiente saudável. Demonstre no navegador:'
Write-Host '1. http://localhost:8080 -> entre pelo Keycloak com o usuário demo e senha local do .env.'
Write-Host '2. Crie uma avaliação e confira decisão, regras, motivos e correlationId.'
Write-Host '3. Consulte o histórico, aplique filtros e gere/baixe o PDF.'
Write-Host '4. Confira o evento no broker e métricas em http://localhost:8080/actuator/prometheus.'
Write-Host 'Não copie tokens, senhas ou CPF completo para a evidência.'

if ($OpenBrowser) {
    Start-Process 'http://localhost:8080'
    Start-Process 'http://localhost:8180'
}
