# Índice de evidências

Este índice é a fonte de consulta para a demonstração. Registre uma evidência somente depois de executar seu comando e substitua os campos entre `<...>` pelos dados observados. Não registre tokens, senhas, CPF completo ou payloads sensíveis.

## Execução comprovada

| Evidência | Comando | Resultado a registrar | Data (UTC) | Commit validado |
| --- | --- | --- | --- | --- |
| Compose limpo e jornada E2E | `./scripts/e2e-compose.ps1` | TAP do `test/e2e/credit-flow.spec.mjs`; serviços saudáveis | `<YYYY-MM-DD>` | `<git rev-parse HEAD>` |
| Testes locais | `./gradlew.bat test --no-daemon` | `BUILD SUCCESSFUL` | `<YYYY-MM-DD>` | `<git rev-parse HEAD>` |
| Testes de contrato/documentação | `node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts"` | TAP sem falhas | `<YYYY-MM-DD>` | `<git rev-parse HEAD>` |
| Relatório JUnit em TAP | `node scripts/junit-to-tap.mjs` | TAP sem falhas | `<YYYY-MM-DD>` | `<git rev-parse HEAD>` |
| Carga nominal | `./scripts/run-load-test.ps1` | Aprovado: 10.000,2/min; p99 334,781119 ms; erro técnico 0%; 50.002 nominais; 0 descartadas | `2026-08-16` | `b8df8f2b1c700939f5adfd7fa8afdf91a2cdc845` |
| Scans e gates de CI | GitHub Actions, job de qualidade | URL do job e artefatos SBOM/scans | `<YYYY-MM-DD>` | `<git rev-parse HEAD>` |

O resultado de uma execução é específico ao commit e ao ambiente. Se o commit mudar, execute novamente e crie um novo registro; não reaproveite a data ou resultado anterior como se validasse a nova revisão.

## Artefatos de apoio

- [Prova Compose E2E](compose-e2e.md): prepara volumes limpos e cobre login, avaliação, histórico, PDF e evento.
- [Cenário de carga](../../performance/README.md): descreve o ensaio; o resultado só é evidência após execução real e registro nesta tabela.
- [Arquitetura](../architecture.md): separa a solução local atual da evolução AWS proposta.

## Arquitetura proposta, não evidência de execução

ECS/Fargate, Aurora Multi-AZ, MSK/EventBridge, WAF, Secrets Manager/KMS, autoscaling e disaster recovery são uma arquitetura de referência. Eles não foram provisionados por este repositório e não devem ser apresentados como serviços executados ou como comprovação de capacidade. Da mesma forma, os thresholds do k6 são objetivo até que um ensaio isolado registre medições reais.
