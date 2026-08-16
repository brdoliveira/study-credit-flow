# Tasks: Crédito rotativo

> feature: credito-rotativo

## T-001 — Preparar projeto Kotlin e testes de especificação [concluida]
- Refs: US-012, AC-045, AC-047
- Arquivos: settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties, onpspec.config.json, src/main/kotlin/com/itau/credit/CreditFlowApplication.kt, src/test/kotlin/com/itau/credit/spec/SpecificationContractTest.kt
- Notas: configurar Java 21, Kotlin, Spring Boot, lint/análise estática e comando de teste usado pelo motor.

## T-002 — Implementar domínio e motor extensível de regras [concluida]
- Refs: US-002, AC-004, AC-005, AC-006, AC-007, AC-008, AC-009, AC-010, AC-011
- Arquivos: src/main/kotlin/com/itau/credit/domain/model/CreditEvaluationContext.kt, src/main/kotlin/com/itau/credit/domain/model/RuleResult.kt, src/main/kotlin/com/itau/credit/domain/model/CreditDecision.kt, src/main/kotlin/com/itau/credit/domain/rule/CreditRule.kt, src/main/kotlin/com/itau/credit/domain/rule/RuleEngine.kt, src/main/kotlin/com/itau/credit/domain/rule/MinimumScoreRule.kt, src/main/kotlin/com/itau/credit/domain/rule/MaxLatePaymentsRule.kt, src/main/kotlin/com/itau/credit/domain/rule/AvailableLimitRule.kt, src/main/kotlin/com/itau/credit/domain/rule/LimitCommitmentRule.kt, src/main/kotlin/com/itau/credit/domain/rule/RecentSpendingTrendRule.kt, src/test/kotlin/com/itau/credit/domain/rule/RuleEngineTest.kt
- Notas: todas as regras executam; severidades BLOCKING e WARNING.

## T-003 — Implementar cálculo do crédito [concluida]
- Refs: US-003, AC-012, AC-013, AC-014
- Arquivos: src/main/kotlin/com/itau/credit/domain/calculation/CreditLimitCalculator.kt, src/main/kotlin/com/itau/credit/domain/calculation/ConfigurableCreditLimitCalculator.kt, src/main/kotlin/com/itau/credit/domain/calculation/CreditCalculationPolicy.kt, src/test/kotlin/com/itau/credit/domain/calculation/CreditLimitCalculatorTest.kt
- Notas: depende da confirmação das premissas de fórmula e arredondamento.

## T-004 — Implementar caso de uso de avaliação [concluida]
- Refs: US-001, US-004, AC-001, AC-003, AC-015, AC-016
- Arquivos: src/main/kotlin/com/itau/credit/application/evaluation/EvaluateRevolvingCreditUseCase.kt, src/main/kotlin/com/itau/credit/application/evaluation/EvaluateCreditCommand.kt, src/main/kotlin/com/itau/credit/application/evaluation/CreditEvaluationResult.kt, src/main/kotlin/com/itau/credit/infrastructure/config/ApplicationConfiguration.kt, src/test/kotlin/com/itau/credit/application/evaluation/EvaluateRevolvingCreditUseCaseTest.kt
- Notas: orquestra regras, decisão, cálculo e portas transacionais.

## T-005 — Implementar API REST e contrato de erros [concluida]
- Refs: US-001, US-006, AC-001, AC-002, AC-003, AC-022, AC-023, AC-024, AC-028, AC-040
- Arquivos: src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationController.kt, src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationRequest.kt, src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationResponse.kt, src/main/kotlin/com/itau/credit/infrastructure/web/DefaultCreditEvaluationApiService.kt, src/main/kotlin/com/itau/credit/infrastructure/web/ApiError.kt, src/main/kotlin/com/itau/credit/infrastructure/web/GlobalExceptionHandler.kt, src/test/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationControllerTest.kt
- Notas: incluir paginação, ordenação, filtros, Location e status padronizados.

## T-006 — Implementar persistência PostgreSQL e auditoria [concluida]
- Refs: US-004, US-006, AC-015, AC-016, AC-017, AC-022, AC-023, AC-024
- Arquivos: src/main/resources/db/migration/V1__credit_evaluation.sql, src/main/kotlin/com/itau/credit/application/port/CreditEvaluationRepository.kt, src/main/kotlin/com/itau/credit/infrastructure/persistence/PostgresCreditEvaluationRepository.kt, src/main/kotlin/com/itau/credit/infrastructure/persistence/CreditEvaluationEntity.kt, src/main/kotlin/com/itau/credit/infrastructure/privacy/CpfProtector.kt, src/test/kotlin/com/itau/credit/infrastructure/persistence/PostgresCreditEvaluationRepositoryIT.kt
- Notas: usar Flyway e Testcontainers; CPF completo não é persistido para consulta.

## T-007 — Implementar idempotência concorrente [concluida]
- Refs: US-005, AC-018, AC-019, AC-020, AC-021
- Arquivos: src/main/resources/db/migration/V2__credit_idempotency.sql, src/main/kotlin/com/itau/credit/application/port/IdempotencyRepository.kt, src/main/kotlin/com/itau/credit/infrastructure/idempotency/PostgresIdempotencyRepository.kt, src/main/kotlin/com/itau/credit/infrastructure/idempotency/CanonicalRequestHasher.kt, src/test/kotlin/com/itau/credit/infrastructure/idempotency/IdempotencyIT.kt
- Notas: validar chave, replay, conflito e corrida em banco real.

## T-008 — Implementar Outbox, publicação e consumo idempotente [concluida]
- Refs: US-009, AC-033, AC-034, AC-035, AC-036
- Arquivos: src/main/resources/db/migration/V3__credit_outbox.sql, src/main/kotlin/com/itau/credit/application/event/CreditEvaluationCompleted.kt, src/main/kotlin/com/itau/credit/infrastructure/outbox/OutboxPublisher.kt, src/main/kotlin/com/itau/credit/infrastructure/messaging/CreditEvaluationEventProducer.kt, src/main/kotlin/com/itau/credit/infrastructure/messaging/IdempotentCreditEvaluationConsumer.kt, src/test/kotlin/com/itau/credit/infrastructure/messaging/CreditEvaluationMessagingIT.kt
- Notas: broker local depende da confirmação de ASM-007.

## T-009 — Implementar autenticação e autorização [concluida]
- Refs: US-008, AC-029, AC-030, AC-031, AC-032
- Arquivos: src/main/kotlin/com/itau/credit/infrastructure/security/SecurityConfiguration.kt, src/main/kotlin/com/itau/credit/infrastructure/security/ScopeAuthoritiesConverter.kt, src/main/resources/application-security.yml, src/test/kotlin/com/itau/credit/infrastructure/security/ApiSecurityTest.kt, docker/keycloak/realm-export.json
- Notas: Resource Server JWT; provedor local depende da confirmação de ASM-006.

## T-010 — Implementar relatório PDF [concluida]
- Refs: US-007, AC-025, AC-026, AC-027, AC-028
- Arquivos: src/main/kotlin/com/itau/credit/application/report/CreditEvaluationReportGenerator.kt, src/main/kotlin/com/itau/credit/application/report/CreditEvaluationReportFilter.kt, src/main/kotlin/com/itau/credit/infrastructure/report/PdfCreditEvaluationReportGenerator.kt, src/main/kotlin/com/itau/credit/infrastructure/web/CreditEvaluationReportController.kt, src/test/kotlin/com/itau/credit/infrastructure/report/PdfCreditEvaluationReportTest.kt
- Notas: registrar a biblioteca escolhida em ADR antes da implementação.

## T-011 — Implementar frontend demonstrativo [concluida]
- Refs: US-011, AC-041, AC-042, AC-043
- Arquivos: src/main/resources/static/index.html, src/main/resources/static/report.html, src/main/resources/static/css/app.css, src/main/resources/static/ts/api.ts, src/main/resources/static/ts/evaluation.ts, src/main/resources/static/ts/report.ts, src/test/kotlin/com/itau/credit/infrastructure/web/FrontendSmokeTest.kt
- Notas: frontend fino, sem lógica de decisão; reutilizar filtros no PDF.

## T-012 — Implementar observabilidade e resiliência [concluida]
- Refs: US-010, AC-037, AC-038, AC-039, AC-040
- Arquivos: src/main/kotlin/com/itau/credit/infrastructure/observability/CorrelationIdFilter.kt, src/main/kotlin/com/itau/credit/infrastructure/observability/CreditMetrics.kt, src/main/kotlin/com/itau/credit/infrastructure/health/DependencyReadinessIndicator.kt, src/main/resources/logback-spring.xml, src/main/resources/application-observability.yml, src/test/kotlin/com/itau/credit/infrastructure/observability/ObservabilityTest.kt
- Notas: controlar cardinalidade e não registrar payload financeiro completo.

## T-013 — Preparar execução local em containers [concluida]
- Refs: US-012, AC-044
- Arquivos: Dockerfile, compose.yaml, .env.example, docker/postgres/init.sql, docker/kafka/README.md, src/main/resources/application.yml
- Notas: aplicação, PostgreSQL, identidade e broker; health checks e volumes explícitos.

## T-014 — Criar pipeline de CI e gates [concluida]
- Refs: US-012, AC-045
- Arquivos: .github/workflows/ci.yml, config/detekt/detekt.yml, .spec/README.md
- Notas: build, testes, análise, audit --ci, imagem e evidência dos gates.

## T-015 — Criar e documentar teste de carga [concluida]
- Refs: US-012, AC-046
- Arquivos: performance/k6/credit-evaluation.js, performance/README.md, src/test/resources/performance/valid-credit-evaluation.json
- Notas: ambiente, warm-up, duração, concorrência, taxa de erro, throughput e percentis reproduzíveis.

## T-016 — Consolidar README, ADRs e arquitetura cloud [concluida]
- Refs: US-012, AC-047
- Arquivos: README.md, docs/architecture.md, docs/adrs/001-modular-monolith.md, docs/adrs/002-postgresql.md, docs/adrs/003-outbox-messaging.md, docs/adrs/004-pdf-library.md, docs/adrs/005-ecs-vs-eks.md, docs/adrs/006-aurora-vs-dynamodb.md, docs/ai-usage.md
- Notas: explicar execução, decisões, limitações, evolução, segurança, operação e uso de IA.
