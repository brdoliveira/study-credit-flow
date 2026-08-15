# Plano de execução — credito-rotativo

> gerado por `onp-spec plano` em 2026-08-15 21:37 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano credito-rotativo`

## Resumo — o que vai acontecer

- **16 tarefa(s) pendente(s)**: 16 em 16 faixa(s) paralela(s) + 0 sequencial(is)
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano credito-rotativo --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/credito-rotativo`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/credito-rotativo-faixa-1` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-001 | Preparar projeto Kotlin e testes de especificação | `gpt-5.6-terra` | medium | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `onpspec.config.json`, `src/test/kotlin/com/itau/credit/spec/SpecificationContractTest.kt` |

#### faixa-2 — branch `spec/credito-rotativo-faixa-2` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-002 | Implementar domínio e motor extensível de regras | `gpt-5.6-terra` | medium | `src/main/kotlin/com/itau/credit/domain/model/CreditEvaluationContext.kt`, `src/main/kotlin/com/itau/credit/domain/model/RuleResult.kt`, `src/main/kotlin/com/itau/credit/domain/model/CreditDecision.kt`, `src/main/kotlin/com/itau/credit/domain/rule/CreditRule.kt`, `src/main/kotlin/com/itau/credit/domain/rule/RuleEngine.kt`, `src/main/kotlin/com/itau/credit/domain/rule/MinimumScoreRule.kt`, `src/main/kotlin/com/itau/credit/domain/rule/MaxLatePaymentsRule.kt`, `src/main/kotlin/com/itau/credit/domain/rule/AvailableLimitRule.kt`, `src/main/kotlin/com/itau/credit/domain/rule/LimitCommitmentRule.kt`, `src/main/kotlin/com/itau/credit/domain/rule/RecentSpendingTrendRule.kt`, `src/test/kotlin/com/itau/credit/domain/rule/RuleEngineTest.kt` |

#### faixa-3 — branch `spec/credito-rotativo-faixa-3` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-003 | Implementar cálculo do crédito | `gpt-5.6-terra` | medium | `src/main/kotlin/com/itau/credit/domain/calculation/CreditLimitCalculator.kt`, `src/main/kotlin/com/itau/credit/domain/calculation/ConfigurableCreditLimitCalculator.kt`, `src/main/kotlin/com/itau/credit/domain/calculation/CreditCalculationPolicy.kt`, `src/test/kotlin/com/itau/credit/domain/calculation/CreditLimitCalculatorTest.kt` |

### Onda 2 — faixa-4 ∥ faixa-5 ∥ faixa-6

#### faixa-4 — branch `spec/credito-rotativo-faixa-4` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-004 | Implementar caso de uso de avaliação | `gpt-5.6-terra` | medium | `src/main/kotlin/com/itau/credit/application/evaluation/EvaluateRevolvingCreditUseCase.kt`, `src/main/kotlin/com/itau/credit/application/evaluation/EvaluateCreditCommand.kt`, `src/main/kotlin/com/itau/credit/application/evaluation/CreditEvaluationResult.kt`, `src/test/kotlin/com/itau/credit/application/evaluation/EvaluateRevolvingCreditUseCaseTest.kt` |

#### faixa-5 — branch `spec/credito-rotativo-faixa-5` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-5`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-005 | Implementar API REST e contrato de erros | `gpt-5.6-terra` | medium | `src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationController.kt`, `src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationRequest.kt`, `src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationResponse.kt`, `src/main/kotlin/com/itau/credit/infrastructure/web/ApiError.kt`, `src/main/kotlin/com/itau/credit/infrastructure/web/GlobalExceptionHandler.kt`, `src/test/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationControllerTest.kt` |

#### faixa-6 — branch `spec/credito-rotativo-faixa-6` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-6`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-006 | Implementar persistência PostgreSQL e auditoria | `gpt-5.6-terra` | medium | `src/main/resources/db/migration/V1__credit_evaluation.sql`, `src/main/kotlin/com/itau/credit/application/port/CreditEvaluationRepository.kt`, `src/main/kotlin/com/itau/credit/infrastructure/persistence/PostgresCreditEvaluationRepository.kt`, `src/main/kotlin/com/itau/credit/infrastructure/persistence/CreditEvaluationEntity.kt`, `src/main/kotlin/com/itau/credit/infrastructure/privacy/CpfProtector.kt`, `src/test/kotlin/com/itau/credit/infrastructure/persistence/PostgresCreditEvaluationRepositoryIT.kt` |

### Onda 3 — faixa-7 ∥ faixa-8 ∥ faixa-9

#### faixa-7 — branch `spec/credito-rotativo-faixa-7` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-7`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-007 | Implementar idempotência concorrente | `gpt-5.6-terra` | medium | `src/main/resources/db/migration/V2__credit_idempotency.sql`, `src/main/kotlin/com/itau/credit/application/port/IdempotencyRepository.kt`, `src/main/kotlin/com/itau/credit/infrastructure/idempotency/PostgresIdempotencyRepository.kt`, `src/main/kotlin/com/itau/credit/infrastructure/idempotency/CanonicalRequestHasher.kt`, `src/test/kotlin/com/itau/credit/infrastructure/idempotency/IdempotencyIT.kt` |

#### faixa-8 — branch `spec/credito-rotativo-faixa-8` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-8`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-008 | Implementar Outbox, publicação e consumo idempotente | `gpt-5.6-terra` | medium | `src/main/resources/db/migration/V3__credit_outbox.sql`, `src/main/kotlin/com/itau/credit/application/event/CreditEvaluationCompleted.kt`, `src/main/kotlin/com/itau/credit/infrastructure/outbox/OutboxPublisher.kt`, `src/main/kotlin/com/itau/credit/infrastructure/messaging/CreditEvaluationEventProducer.kt`, `src/main/kotlin/com/itau/credit/infrastructure/messaging/IdempotentCreditEvaluationConsumer.kt`, `src/test/kotlin/com/itau/credit/infrastructure/messaging/CreditEvaluationMessagingIT.kt` |

#### faixa-9 — branch `spec/credito-rotativo-faixa-9` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-9`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-009 | Implementar autenticação e autorização | `gpt-5.6-terra` | medium | `src/main/kotlin/com/itau/credit/infrastructure/security/SecurityConfiguration.kt`, `src/main/kotlin/com/itau/credit/infrastructure/security/ScopeAuthoritiesConverter.kt`, `src/main/resources/application-security.yml`, `src/test/kotlin/com/itau/credit/infrastructure/security/ApiSecurityTest.kt`, `docker/keycloak/realm-export.json` |

### Onda 4 — faixa-10 ∥ faixa-11 ∥ faixa-12

#### faixa-10 — branch `spec/credito-rotativo-faixa-10` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-10`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-010 | Implementar relatório PDF | `gpt-5.6-terra` | medium | `src/main/kotlin/com/itau/credit/application/report/CreditEvaluationReportGenerator.kt`, `src/main/kotlin/com/itau/credit/application/report/CreditEvaluationReportFilter.kt`, `src/main/kotlin/com/itau/credit/infrastructure/report/PdfCreditEvaluationReportGenerator.kt`, `src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationReportController.kt`, `src/test/kotlin/com/itau/credit/infrastructure/report/PdfCreditEvaluationReportTest.kt` |

#### faixa-11 — branch `spec/credito-rotativo-faixa-11` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-11`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-011 | Implementar frontend demonstrativo | `gpt-5.6-terra` | medium | `src/main/resources/static/index.html`, `src/main/resources/static/report.html`, `src/main/resources/static/css/app.css`, `src/main/resources/static/ts/api.ts`, `src/main/resources/static/ts/evaluation.ts`, `src/main/resources/static/ts/report.ts`, `src/test/kotlin/com/itau/credit/infrastructure/web/FrontendSmokeTest.kt` |

#### faixa-12 — branch `spec/credito-rotativo-faixa-12` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-12`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-012 | Implementar observabilidade e resiliência | `gpt-5.6-terra` | medium | `src/main/kotlin/com/itau/credit/infrastructure/observability/CorrelationIdFilter.kt`, `src/main/kotlin/com/itau/credit/infrastructure/observability/CreditMetrics.kt`, `src/main/kotlin/com/itau/credit/infrastructure/health/DependencyReadinessIndicator.kt`, `src/main/resources/logback-spring.xml`, `src/main/resources/application-observability.yml`, `src/test/kotlin/com/itau/credit/infrastructure/observability/ObservabilityTest.kt` |

### Onda 5 — faixa-13 ∥ faixa-14 ∥ faixa-15

#### faixa-13 — branch `spec/credito-rotativo-faixa-13` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-13`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-013 | Preparar execução local em containers | `gpt-5.6-terra` | medium | `Dockerfile`, `compose.yaml`, `.env.example`, `docker/postgres/init.sql`, `docker/kafka/README.md` |

#### faixa-14 — branch `spec/credito-rotativo-faixa-14` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-14`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-014 | Criar pipeline de CI e gates | `gpt-5.6-terra` | medium | `.github/workflows/ci.yml`, `config/detekt/detekt.yml`, `.spec/README.md` |

#### faixa-15 — branch `spec/credito-rotativo-faixa-15` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-15`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-015 | Criar e documentar teste de carga | `gpt-5.6-terra` | medium | `performance/k6/credit-evaluation.js`, `performance/README.md`, `src/test/resources/performance/valid-credit-evaluation.json` |

### Onda 6 — faixa-16

#### faixa-16 — branch `spec/credito-rotativo-faixa-16` — worktree `../onp-worktrees/study-credit-flow-credito-rotativo-faixa-16`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-016 | Consolidar README, ADRs e arquitetura cloud | `gpt-5.6-terra` | medium | `README.md`, `docs/architecture.md`, `docs/adrs/001-modular-monolith.md`, `docs/adrs/002-postgresql.md`, `docs/adrs/003-outbox-messaging.md`, `docs/adrs/004-pdf-library.md`, `docs/adrs/005-ecs-vs-eks.md`, `docs/adrs/006-aurora-vs-dynamodb.md`, `docs/ai-usage.md` |

## Gestão de branches e commits

1. branch de trabalho `spec/credito-rotativo` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify credito-rotativo` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Codex headless (codex exec)

```bash
bash .spec/features/credito-rotativo/executar-tarefas.sh
```

Cada faixa roda `codex exec` com **janela de contexto limpa**, no seu worktree, com
`--model` e `model_reasoning_effort` já definidos por tarefa e sandbox `workspace-write`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/study-credit-flow-credito-rotativo-logs/`.

**Confirmação de custos — antes de executar**: os modelos e esforços por
tarefa estão nas tabelas acima; o agente CONFIRMA com o usuário se estão
dentro da licença/cota dele (modelo forte + esforço alto torra tokens).
Para gastar menos: `onp-spec plano credito-rotativo --modelo gpt-5.6-luna --esforco baixo`
(tudo) ou por tarefa `onp-spec tarefa credito-rotativo T-xxx --modelo <m> --esforco <nível>` — e regenere o plano.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo credito-rotativo --tabela   # a tabela de andamento
onp-spec resumo credito-rotativo            # o resumo em texto
```

