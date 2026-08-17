# Plano de execução — prontidao-entrega

> gerado por `onp-spec plano` em 2026-08-16 20:50 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano prontidao-entrega`

## Resumo — o que vai acontecer

- **11 tarefa(s) pendente(s)**: 11 em 7 faixa(s) paralela(s) + 0 sequencial(is)
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano prontidao-entrega --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/prontidao-entrega`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/prontidao-entrega-faixa-1` — worktree `../onp-worktrees/study-credit-flow-prontidao-entrega-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-017 | Implementar login OIDC pelo BFF | `gpt-5.6-terra` | medium | `build.gradle.kts`, `compose.yaml`, `docker/keycloak/realm-export.json`, `src/main/resources/application-security.yml`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/SecurityConfiguration.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/OidcSessionAuthoritiesMapper.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/SessionController.kt`, `src/main/resources/static/index.html`, `src/main/resources/static/report.html`, `src/main/resources/static/ts/api.ts`, `src/main/resources/static/ts/session.ts`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/OidcBrowserSecurityIT.kt` |
| T-022 | Criar prova ponta a ponta do Docker Compose | `gpt-5.6-terra` | medium | `compose.yaml`, `.env.example`, `docker/keycloak/realm-export.json`, `scripts/e2e-compose.ps1`, `test/e2e/credit-flow.spec.mjs`, `test/e2e/helpers.mjs`, `docs/evidence/compose-e2e.md` |
| T-025 | Publicar e testar o contrato OpenAPI | `gpt-5.6-terra` | medium | `build.gradle.kts`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/OpenApiConfiguration.kt`, `src/main/resources/openapi/credit-evaluations.yaml`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/OpenApiContractIT.kt`, `README.md` |
| T-027 | Consolidar roteiro e índice de evidências | `gpt-5.6-terra` | medium | `README.md`, `docs/evidence/README.md`, `docs/architecture.md`, `docs/ai-usage.md`, `scripts/demo.ps1`, `test/delivery-documentation.test.mjs` |

#### faixa-2 — branch `spec/prontidao-entrega-faixa-2` — worktree `../onp-worktrees/study-credit-flow-prontidao-entrega-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-018 | Corrigir a semântica HTTP da idempotência | `gpt-5.6-terra` | medium | `src/main/kotlin/io/github/brdoliveira/creditflow/application/port/IdempotencyRepository.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/idempotency/PostgresIdempotencyRepository.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/DefaultCreditEvaluationApiService.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationController.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/IdempotentCreditEvaluationHttpIT.kt` |

#### faixa-3 — branch `spec/prontidao-entrega-faixa-3` — worktree `../onp-worktrees/study-credit-flow-prontidao-entrega-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-019 | Conectar Outbox PostgreSQL ao Kafka | `gpt-5.6-terra` | medium | `src/main/resources/db/migration/V4__outbox_runtime.sql`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox/PostgresOutboxStore.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox/OutboxPublisher.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox/OutboxSchedulingConfiguration.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/KafkaBrokerPublisher.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationEventProducer.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/OutboxKafkaIT.kt` |

### Onda 2 — faixa-4 ∥ faixa-5 ∥ faixa-6

#### faixa-4 — branch `spec/prontidao-entrega-faixa-4` — worktree `../onp-worktrees/study-credit-flow-prontidao-entrega-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-020 | Implementar consumidor Kafka idempotente | `gpt-5.6-terra` | medium | `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationKafkaListener.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/PostgresProcessedEventStore.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/IdempotentCreditEvaluationConsumer.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/IdempotentKafkaConsumerIT.kt` |

#### faixa-5 — branch `spec/prontidao-entrega-faixa-5` — worktree `../onp-worktrees/study-credit-flow-prontidao-entrega-faixa-5`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-021 | Integrar métricas e health checks ao runtime | `gpt-5.6-terra` | medium | `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/CreditMetrics.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/ObservedCreditEvaluationService.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/GlobalExceptionHandler.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/health/DependencyReadinessIndicator.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/config/ApplicationConfiguration.kt`, `src/main/resources/application-observability.yml`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/RuntimeObservabilityIT.kt` |

#### faixa-6 — branch `spec/prontidao-entrega-faixa-6` — worktree `../onp-worktrees/study-credit-flow-prontidao-entrega-faixa-6`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-023 | Executar e registrar o ensaio de carga | `gpt-5.6-terra` | medium | `performance/k6/credit-evaluation.js`, `performance/k6/summary.js`, `performance/README.md`, `scripts/run-load-test.ps1`, `docs/evidence/load-test-summary.json`, `docs/evidence/load-test.md`, `src/test/resources/performance/credit-evaluation.test.js` |

### Onda 3 — faixa-7

#### faixa-7 — branch `spec/prontidao-entrega-faixa-7` — worktree `../onp-worktrees/study-credit-flow-prontidao-entrega-faixa-7`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-024 | Tornar CI e supply chain reproduzíveis | `gpt-5.6-terra` | medium | `.github/workflows/ci.yml`, `tools/onp-spec/onp-spec.mjs`, `tools/onp-spec/lib`, `package.json`, `package-lock.json`, `scripts/junit-to-tap.mjs`, `config/security/trivy.yaml`, `config/security/gitleaks.toml`, `test/ci-pipeline.test.mjs` |
| T-026 | Criar infraestrutura AWS de referência | `gpt-5.6-terra` | medium | `infrastructure/terraform/README.md`, `infrastructure/terraform/versions.tf`, `infrastructure/terraform/variables.tf`, `infrastructure/terraform/main.tf`, `infrastructure/terraform/outputs.tf`, `infrastructure/terraform/modules/network/main.tf`, `infrastructure/terraform/modules/service/main.tf`, `infrastructure/terraform/modules/database/main.tf`, `infrastructure/terraform/modules/messaging/main.tf`, `infrastructure/terraform/modules/observability/main.tf`, `test/terraform-contract.test.mjs`, `.github/workflows/ci.yml` |

## Gestão de branches e commits

1. branch de trabalho `spec/prontidao-entrega` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify prontidao-entrega` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Codex headless (codex exec)

```bash
bash .spec/features/prontidao-entrega/executar-tarefas.sh
```

Cada faixa roda `codex exec` com **janela de contexto limpa**, no seu worktree, com
`--model` e `model_reasoning_effort` já definidos por tarefa e sandbox `workspace-write`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/study-credit-flow-prontidao-entrega-logs/`.

**Confirmação de custos — antes de executar**: os modelos e esforços por
tarefa estão nas tabelas acima; o agente CONFIRMA com o usuário se estão
dentro da licença/cota dele (modelo forte + esforço alto torra tokens).
Para gastar menos: `onp-spec plano prontidao-entrega --modelo gpt-5.6-luna --esforco baixo`
(tudo) ou por tarefa `onp-spec tarefa prontidao-entrega T-xxx --modelo <m> --esforco <nível>` — e regenere o plano.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo prontidao-entrega --tabela   # a tabela de andamento
onp-spec resumo prontidao-entrega            # o resumo em texto
```

