[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string]$BaseUrl,
    [string]$Authorization,
    [string]$Environment = 'isolated-local',
    [string]$Resources = 'document the load-generator, API, database and broker resources here',
    [string]$EvidenceFile = 'docs/evidence/load-test-summary.json'
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    throw 'k6 was not found on PATH. Install k6 before running the nominal load test.'
}

$env:BASE_URL = $BaseUrl
$env:LOAD_TEST_COMMIT = (git rev-parse HEAD).Trim()
$env:LOAD_TEST_EXECUTED_AT_UTC = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$env:LOAD_TEST_ENVIRONMENT = $Environment
$env:LOAD_TEST_RESOURCES = $Resources
$env:LOAD_TEST_EVIDENCE_FILE = $EvidenceFile
if ($Authorization) { $env:AUTHORIZATION = $Authorization } else { Remove-Item Env:AUTHORIZATION -ErrorAction SilentlyContinue }

try {
    k6 run performance/k6/credit-evaluation.js
    if ($LASTEXITCODE -ne 0) { throw "k6 failed with exit code $LASTEXITCODE." }
}
finally { Remove-Item Env:AUTHORIZATION -ErrorAction SilentlyContinue }
