# Teste de carga — avaliação de crédito

Este cenário verifica o AC-068: na fase nominal, a API recebe **10.000 avaliações por minuto** durante cinco minutos, depois de um aquecimento de um minuto a 1.000 avaliações por minuto. O k6 reprova a execução se o p99 da fase nominal chegar a 1 segundo, se os erros técnicos chegarem a 1% ou se houver iterações descartadas.

## Pré-requisitos

- k6 instalado;
- API e dependências locais saudáveis;
- um token JWT válido com o escopo `credit:write` quando a segurança estiver habilitada.

O payload está em `src/test/resources/performance/valid-credit-evaluation.json`. Cada iteração envia novas chaves de idempotência e de correlação; o corpo da requisição não é registrado pelo teste.

## Executar

### macOS e Linux — execução automática

Com o Docker em execução, o runner automatiza todo o fluxo em uma pilha isolada: constrói e inicia os serviços, aguarda os health checks, obtém o token no Keycloak, executa o k6, grava o relatório e remove os containers e volumes.

```bash
./scripts/run-load-test.sh
```

Por padrão, a API isolada usa a porta `18080`, o Keycloak `18180`, o PostgreSQL `15432` e o Kafka `19092`, sem interferir na aplicação principal. A senha é lida de `CREDIT_DEMO_PASSWORD` no `.env`. As evidências ficam em `.context/load-test-summary.json` e `.context/load-test-report.pdf`; o PDF apresenta indicadores, gráfico comparativo, configuração e tabela dos critérios de aprovação.

Para escolher outro arquivo ou manter a pilha depois da execução:

```bash
./scripts/run-load-test.sh --evidence docs/evidence/load-test-summary.json --pdf docs/evidence/load-test-report.pdf --keep-stack
```

Execute `./scripts/run-load-test.sh --help` para consultar as variáveis de configuração disponíveis.

### PowerShell — API já iniciada

No PowerShell, defina o endereço da API e, quando necessário, o cabeçalho de autorização. Não grave tokens em arquivos ou no histórico do repositório.

```powershell
$env:BASE_URL = 'http://localhost:8080'
$env:AUTHORIZATION = "Bearer $env:CREDIT_LOAD_TEST_TOKEN"
./scripts/run-load-test.ps1 -BaseUrl $env:BASE_URL -Authorization $env:AUTHORIZATION
```

Para executar sem autenticação (por exemplo, em um ambiente de teste isolado), deixe `AUTHORIZATION` ausente:

```powershell
$env:BASE_URL = 'http://localhost:8080'
Remove-Item Env:AUTHORIZATION -ErrorAction SilentlyContinue
./scripts/run-load-test.ps1 -BaseUrl $env:BASE_URL
```

O teste tem duas fases:

| Fase | Taxa | Duração | Objetivo |
| --- | ---: | ---: | --- |
| Aquecimento | 1.000 avaliações/minuto | 1 minuto | Estabilizar conexões, JVM e pools. |
| Nominal | 10.000 avaliações/minuto | 5 minutos | Medir o objetivo de carga do AC-068. |

O executor `constant-arrival-rate` mantém a taxa de chegadas, em vez de depender do tempo de resposta. Comece com 250 VUs pré-alocados e permita até 1.000; se houver `dropped_iterations`, aumente a capacidade de geração antes de atribuir o problema à API.

## Critério de aprovação

A execução passa somente quando, na fase `nominal`:

- `http_req_duration` tem `p(99) < 1000 ms`;
- `technical_error_rate < 1%` — erro técnico é falha de transporte ou resposta `5xx`;
- não há `dropped_iterations`;
- respostas válidas `2xx` (aprovadas ou reprovadas por regra de negócio) contam como processamento concluído.

O código de saída do k6 é diferente de zero quando algum limiar falha. O runner PowerShell grava `docs/evidence/load-test-summary.json`; o runner automático para macOS/Linux grava `.context/load-test-summary.json` por padrão. O relatório contém commit, data UTC, ambiente, recursos, configuração, taxa observada, `dropped_iterations`, p99, `technical_error_rate` e resultado de cada threshold. Não use o placeholder versionado como prova: ele precisa ser substituído pela saída de uma execução real.

## Verificar a configuração sem gerar carga

O teste Node abaixo valida o contrato do cenário, seus limiares e o payload usado pelo k6:

```powershell
node --test src/test/resources/performance/credit-evaluation.test.js
```
