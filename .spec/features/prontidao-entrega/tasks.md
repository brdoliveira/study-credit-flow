# Tasks: Prontidão de entrega e integração real

> feature: prontidao-entrega

## T-017 — Implementar login OIDC pelo BFF [concluida]
- Refs: US-013, AC-048, AC-049, AC-050, AC-051, AC-052
- Arquivos: build.gradle.kts, compose.yaml, docker/keycloak/realm-export.json, src/main/resources/application-security.yml, src/main/kotlin/com/itau/credit/infrastructure/security/SecurityConfiguration.kt, src/main/kotlin/com/itau/credit/infrastructure/security/OidcSessionAuthoritiesMapper.kt, src/main/kotlin/com/itau/credit/infrastructure/web/SessionController.kt, src/main/resources/static/index.html, src/main/resources/static/report.html, src/main/resources/static/ts/api.ts, src/main/resources/static/ts/session.ts, src/test/kotlin/com/itau/credit/infrastructure/security/OidcBrowserSecurityIT.kt
- Notas: depende da confirmação de Q-004; manter Resource Server para clientes máquina a máquina.

## T-018 — Corrigir a semântica HTTP da idempotência [concluida]
- Refs: US-014, AC-053, AC-054, AC-055
- Arquivos: src/main/kotlin/com/itau/credit/application/port/IdempotencyRepository.kt, src/main/kotlin/com/itau/credit/infrastructure/idempotency/PostgresIdempotencyRepository.kt, src/main/kotlin/com/itau/credit/infrastructure/web/DefaultCreditEvaluationApiService.kt, src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationController.kt, src/test/kotlin/com/itau/credit/infrastructure/web/IdempotentCreditEvaluationHttpIT.kt
- Notas: o controller precisa distinguir criação de replay sem comparar payloads fora do repositório.

## T-019 — Conectar Outbox PostgreSQL ao Kafka [concluida]
- Refs: US-015, AC-056, AC-057, AC-058, AC-060
- Arquivos: src/main/resources/db/migration/V4__outbox_runtime.sql, src/main/kotlin/com/itau/credit/infrastructure/outbox/PostgresOutboxStore.kt, src/main/kotlin/com/itau/credit/infrastructure/outbox/OutboxPublisher.kt, src/main/kotlin/com/itau/credit/infrastructure/outbox/OutboxSchedulingConfiguration.kt, src/main/kotlin/com/itau/credit/infrastructure/messaging/KafkaBrokerPublisher.kt, src/main/kotlin/com/itau/credit/infrastructure/messaging/CreditEvaluationEventProducer.kt, src/test/kotlin/com/itau/credit/infrastructure/messaging/OutboxKafkaIT.kt
- Notas: usar confirmação do broker, lote com concorrência segura e retry persistido.

## T-020 — Implementar consumidor Kafka idempotente [concluida]
- Refs: US-015, AC-059, AC-060
- Arquivos: src/main/kotlin/com/itau/credit/infrastructure/messaging/CreditEvaluationKafkaListener.kt, src/main/kotlin/com/itau/credit/infrastructure/messaging/PostgresProcessedEventStore.kt, src/main/kotlin/com/itau/credit/infrastructure/messaging/IdempotentCreditEvaluationConsumer.kt, src/test/kotlin/com/itau/credit/infrastructure/messaging/IdempotentKafkaConsumerIT.kt
- Notas: registro de `eventId` e efeito devem compartilhar a mesma transação local.

## T-021 — Integrar métricas e health checks ao runtime [concluida]
- Refs: US-016, AC-061, AC-062, AC-063, AC-064
- Arquivos: src/main/kotlin/com/itau/credit/infrastructure/observability/CreditMetrics.kt, src/main/kotlin/com/itau/credit/infrastructure/observability/ObservedCreditEvaluationService.kt, src/main/kotlin/com/itau/credit/infrastructure/web/GlobalExceptionHandler.kt, src/main/kotlin/com/itau/credit/infrastructure/health/DependencyReadinessIndicator.kt, src/main/kotlin/com/itau/credit/infrastructure/config/ApplicationConfiguration.kt, src/main/resources/application-observability.yml, src/test/kotlin/com/itau/credit/infrastructure/observability/RuntimeObservabilityIT.kt
- Notas: impedir dupla contagem e tags de alta cardinalidade.

## T-022 — Criar prova ponta a ponta do Docker Compose [concluida]
- Refs: US-013, US-015, US-017, AC-052, AC-060, AC-065, AC-066, AC-067
- Arquivos: compose.yaml, .env.example, docker/keycloak/realm-export.json, scripts/e2e-compose.ps1, test/e2e/credit-flow.spec.mjs, test/e2e/helpers.mjs, docs/evidence/compose-e2e.md
- Notas: executar a partir de volumes limpos; não versionar `.env`, tokens ou dados pessoais reais.

## T-023 — Executar e registrar o ensaio de carga [concluida]
- Refs: US-018, AC-068, AC-069, AC-070
- Arquivos: performance/k6/credit-evaluation.js, performance/k6/summary.js, performance/README.md, scripts/run-load-test.ps1, docs/evidence/load-test-summary.json, docs/evidence/load-test.md, src/test/resources/performance/credit-evaluation.test.js
- Notas: depende da confirmação de Q-005; o resumo deve separar falha técnica de decisão de negócio.

## T-024 — Tornar CI e supply chain reproduzíveis [concluida]
- Refs: US-019, AC-071, AC-072, AC-073
- Arquivos: .github/workflows/ci.yml, tools/onp-spec/onp-spec.mjs, tools/onp-spec/lib, package.json, package-lock.json, scripts/junit-to-tap.mjs, config/security/trivy.yaml, config/security/gitleaks.toml, test/ci-pipeline.test.mjs
- Notas: versionar ou fixar o motor ONP no repositório; gerar SBOM e impedir imagem antes dos gates.

## T-025 — Publicar e testar o contrato OpenAPI [concluida]
- Refs: US-020, AC-074, AC-075
- Arquivos: build.gradle.kts, src/main/kotlin/com/itau/credit/infrastructure/web/OpenApiConfiguration.kt, src/main/resources/openapi/credit-evaluations.yaml, src/test/kotlin/com/itau/credit/infrastructure/web/OpenApiContractIT.kt, README.md
- Notas: documentar OAuth2, idempotência, replay, correlação, erros e exemplos mascarados.

## T-026 — Criar infraestrutura AWS de referência [concluida]
- Refs: US-021, AC-076, AC-077
- Arquivos: infrastructure/terraform/README.md, infrastructure/terraform/versions.tf, infrastructure/terraform/variables.tf, infrastructure/terraform/main.tf, infrastructure/terraform/outputs.tf, infrastructure/terraform/modules/network/main.tf, infrastructure/terraform/modules/service/main.tf, infrastructure/terraform/modules/database/main.tf, infrastructure/terraform/modules/messaging/main.tf, infrastructure/terraform/modules/observability/main.tf, test/terraform-contract.test.mjs, .github/workflows/ci.yml
- Notas: somente `fmt`, `validate` e scan; não executar `apply`.

## T-027 — Consolidar roteiro e índice de evidências [concluida]
- Refs: US-017, US-022, AC-065, AC-066, AC-067, AC-078, AC-079
- Arquivos: README.md, docs/evidence/README.md, docs/architecture.md, docs/ai-usage.md, scripts/demo.ps1, test/delivery-documentation.test.mjs
- Notas: separar claramente execução comprovada de arquitetura proposta.
