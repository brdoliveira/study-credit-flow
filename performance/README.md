# Teste de carga — avaliação de crédito

Este cenário verifica o AC-046: na fase nominal, a API recebe **10.000 avaliações por minuto** durante cinco minutos, depois de um aquecimento de um minuto a 1.000 avaliações por minuto. O k6 reprova a execução se o p99 da fase nominal chegar a 1 segundo ou se os erros técnicos chegarem a 1%.

## Pré-requisitos

- k6 instalado;
- API e dependências locais saudáveis;
- um token JWT válido com o escopo `credit:write` quando a segurança estiver habilitada.

O payload está em `src/test/resources/performance/valid-credit-evaluation.json`. Cada iteração envia novas chaves de idempotência e de correlação; o corpo da requisição não é registrado pelo teste.

## Executar

No PowerShell, defina o endereço da API e, quando necessário, o cabeçalho de autorização. Não grave tokens em arquivos ou no histórico do repositório.

```powershell
$env:BASE_URL = 'http://localhost:8080'
$env:AUTHORIZATION = "Bearer $env:CREDIT_LOAD_TEST_TOKEN"
k6 run performance/k6/credit-evaluation.js
```

Para executar sem autenticação (por exemplo, em um ambiente de teste isolado), deixe `AUTHORIZATION` ausente:

```powershell
$env:BASE_URL = 'http://localhost:8080'
Remove-Item Env:AUTHORIZATION -ErrorAction SilentlyContinue
k6 run performance/k6/credit-evaluation.js
```

O teste tem duas fases:

| Fase | Taxa | Duração | Objetivo |
| --- | ---: | ---: | --- |
| Aquecimento | 1.000 avaliações/minuto | 1 minuto | Estabilizar conexões, JVM e pools. |
| Nominal | 10.000 avaliações/minuto | 5 minutos | Medir o objetivo de carga do AC-046. |

O executor `constant-arrival-rate` mantém a taxa de chegadas, em vez de depender do tempo de resposta. Comece com 250 VUs pré-alocados e permita até 1.000; se houver `dropped_iterations`, aumente a capacidade de geração antes de atribuir o problema à API.

## Critério de aprovação

A execução passa somente quando, na fase `nominal`:

- `http_req_duration` tem `p(99) < 1000 ms`;
- `technical_error_rate < 1%` — erro técnico é falha de transporte ou resposta `5xx`;
- todas as requisições válidas recebem `201 Created`.

O código de saída do k6 é diferente de zero quando algum limiar falha. Guarde o resumo do terminal como evidência da execução, incluindo taxa observada, `dropped_iterations`, p99 e `technical_error_rate`.

## Verificar a configuração sem gerar carga

O teste Node abaixo valida o contrato do cenário, seus limiares e o payload usado pelo k6:

```powershell
node --test src/test/resources/performance/credit-evaluation.test.js
```
