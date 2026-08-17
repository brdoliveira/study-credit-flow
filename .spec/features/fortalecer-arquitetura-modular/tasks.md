# Tasks: Fortalecer arquitetura modular

> feature: fortalecer-arquitetura-modular

## T-036 — Impor todas as fronteiras arquiteturais [concluida]
- Refs: US-027, AC-090
- Arquivos: test/kotlin-architecture.test.mjs, src/test/kotlin/io/github/brdoliveira/creditflow/spec/SpecificationContractTest.kt
- Modelo: gpt-5.6-terra
- Esforço: alto
- Notas: Substituir verificações positivas pontuais por regras genéricas de dependência e impedir sucesso sobre diretório vazio.

## T-037 — Consolidar a organização por feature [concluida]
- Refs: US-027, US-028, AC-091, AC-092
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/application/event/CreditEvaluationCompleted.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/idempotency/CanonicalRequestHasher.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/CreditMetrics.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox, src/test/kotlin/io/github/brdoliveira/creditflow/domain, src/test/kotlin/io/github/brdoliveira/creditflow/application, src/test/kotlin/io/github/brdoliveira/creditflow/evaluation, docs/architecture.md
- Modelo: gpt-5.6-terra
- Esforço: alto
- Notas: Mover código específico para `evaluation` e alinhar os testes ao namespace produtivo; manter bootstrap, segurança, health e correlação como infraestrutura transversal.

## T-038 — Tipar o filtro de decisão de ponta a ponta [concluida]
- Refs: US-028, AC-093
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/CreditEvaluationFilter.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/dto/CreditEvaluationSearchCriteria.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/CreditEvaluationController.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/CreditEvaluationReportController.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/PostgresCreditEvaluationRepository.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationControllerTest.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/persistence/PostgresCreditEvaluationRepositoryIT.kt
- Modelo: gpt-5.6-terra
- Esforço: medio
- Notas: Converter na fronteira HTTP e manter o enum dentro da aplicação e da porta de persistência.

## T-039 — Tornar a classe Kotlin a fonte do evento da outbox [concluida]
- Refs: US-028, AC-094
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/event/CreditEvaluationCompleted.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/PostgresCreditEvaluationRepository.kt, src/main/resources/db/migration/V5__explicit_credit_outbox.sql, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationMessagingIT.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/OutboxKafkaIT.kt
- Modelo: gpt-5.6-terra
- Esforço: alto
- Notas: Remover o trigger por migração nova e inserir o payload serializado pelo adaptador de persistência na mesma transação.

## T-040 — Atualizar documentação e provar compatibilidade [concluida]
- Refs: US-029, AC-095
- Arquivos: docs/architecture.md, docs/adrs/001-modular-monolith.md, test/kotlin-architecture.test.mjs, src/test/kotlin, test
- Modelo: gpt-5.6-terra
- Esforço: medio
- Notas: Atualizar a decisão arquitetural, executar testes Kotlin/Node, Detekt, verify e audit.
