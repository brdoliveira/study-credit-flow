# Ensaio de carga nominal

`scripts/run-load-test.ps1` executa um aquecimento de um minuto e uma fase nominal de 10.000 avaliações por minuto durante cinco minutos. Ao concluir, o k6 grava o resumo sanitizado em `load-test-summary.json`.

## Evidência atual

Ainda não há uma medição real neste checkout: o JSON está marcado como `not-executed`. Ele não comprova a capacidade até que o comando seja executado em ambiente isolado e com recursos documentados.

## Registrar uma medição real

```powershell
$token = $env:CREDIT_LOAD_TEST_TOKEN
./scripts/run-load-test.ps1 -BaseUrl 'http://localhost:8080' -Authorization "Bearer $token" -Environment 'isolated-load-host' -Resources 'k6=4 vCPU/8 GB; api=4 vCPU/8 GB; postgres=2 vCPU/4 GB; broker=2 vCPU/4 GB'
```

O runner preenche commit e data UTC. O token só é passado ao k6 pelo ambiente, é removido ao terminar e não entra no resumo. Uma evidência aprovada indica `executionStatus: completed`, `passed: true`, taxa nominal de pelo menos 10.000/min, p99 menor que 1.000 ms, erro técnico menor que 1% e zero `droppedIterations`.

Erro técnico é somente falha de transporte (`status 0`) ou HTTP `5xx`. Toda decisão de crédito válida em `2xx`, inclusive reprovação de negócio, conta como processamento concluído e não como erro técnico.
