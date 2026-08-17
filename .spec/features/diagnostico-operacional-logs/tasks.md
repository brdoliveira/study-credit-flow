# Tasks: Diagnóstico operacional por logs

> feature: diagnostico-operacional-logs

## T-042 — Criar fundação segura de logging estruturado [pendente]

- Refs: US-033, AC-100, AC-101
- Arquivos: src/main/resources/logback-spring.xml, src/main/resources/application-observability.yml, src/test/kotlin/io/github/brdoliveira/creditflow/platform/observability/StructuredLoggingIT.kt
- Modelo: gpt-5.6-terra
- Esforço: alto
- Notas: usar `StructuredLogEncoder` Logstash do Spring Boot; expor somente tipos e frames de exceção, nunca mensagens ou payloads potencialmente sensíveis.

## T-043 — Registrar falhas HTTP técnicas [pendente]

- Refs: US-031, AC-097, AC-101
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/platform/observability/SafeExceptionDetails.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/error/GlobalExceptionHandler.kt, src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/observability/ObservabilityTest.kt, src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/observability/RuntimeObservabilityIT.kt, src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/error/GlobalExceptionLoggingTest.kt
- Modelo: gpt-5.6-terra
- Esforço: medio
- Notas: manter respostas genéricas; logar apenas contexto operacional seguro e detalhes sanitizados da exceção.

## T-044 — Propagar e registrar contexto assíncrono [pendente]

- Refs: US-032, AC-098, AC-099, AC-101
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/platform/observability/CorrelationLogContext.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/outbox/OutboxPublisher.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/messaging/CreditEvaluationKafkaListener.kt, src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/messaging/AsyncOperationalLoggingTest.kt
- Modelo: gpt-5.6-terra
- Esforço: alto
- Notas: `WARN` em retry, `ERROR` em falha, `INFO` em duplicata e `DEBUG` em sucesso; restaurar o MDC em `finally`.

## T-045 — Documentar operação e provar compatibilidade [pendente]

- Refs: US-031, US-032, US-033, AC-100, AC-101
- Arquivos: README.md, docs/architecture.md, .env.example, test/kotlin-architecture.test.mjs, .spec/verification/diagnostico-operacional-logs.json
- Modelo: gpt-5.6-terra
- Esforço: medio
- Notas: documentar busca por correlação, níveis e campos; executar testes Kotlin/Node, Detekt, verify e audit.
