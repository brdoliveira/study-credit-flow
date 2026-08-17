# Tasks: Arquitetura Kotlin

> feature: arquitetura-kotlin

## T-030 — Proteger convenções arquiteturais com testes [pendente]

- Refs: US-024, AC-082, AC-083, AC-084, US-025, AC-085, AC-086, AC-087
- Arquivos: test/kotlin-architecture.test.mjs, .spec/features/arquitetura-kotlin/spec.md, .spec/features/arquitetura-kotlin/design.md, .spec/features/arquitetura-kotlin/tasks.md
- Modelo: gpt-5.4
- Esforço: alto
- Notas: criar verificações estruturais para nomes de arquivos, KDoc em português, dependências e separação do adaptador web.

## T-031 — Consolidar domínio e núcleo da aplicação [pendente]

- Refs: US-025, AC-085, AC-086, AC-087, US-026, AC-088
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/domain/CreditEvaluation.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/domain/CreditDecision.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/domain/RuleResult.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/EvaluateCreditCommand.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/EvaluateRevolvingCreditUseCase.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/FindCreditEvaluationUseCase.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/ListCreditEvaluationsUseCase.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/port/CreditEvaluationRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/port/IdempotencyRepository.kt
- Modelo: gpt-5.4
- Esforço: xalto
- Notas: aplicação usa domínio diretamente; eliminar enums, decisões e snapshots duplicados; portas representam apenas persistência e idempotência.

## T-032 — Separar o adaptador web e remover a fachada de serviço [pendente]

- Refs: US-024, AC-082, AC-084, US-025, AC-087, US-026, AC-088
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/CreditEvaluationController.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/CreditEvaluationReportController.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/dto/CreditEvaluationRequest.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/dto/CreditEvaluationResponse.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/dto/CreditEvaluationPageResponse.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/mapper/CreditEvaluationWebMapper.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/error/GlobalExceptionHandler.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/error/ApiError.kt
- Modelo: gpt-5.4
- Esforço: alto
- Notas: controllers chamam casos de uso; DTOs, mapeadores e erros ficam em subpacotes próprios; remover DefaultCreditEvaluationApiService.

## T-033 — Adaptar persistência, idempotência, relatório e configuração [pendente]

- Refs: US-025, AC-086, AC-087, US-026, AC-088
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/CreditEvaluationEntity.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/PostgresCreditEvaluationRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/idempotency/PostgresIdempotencyRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/report/GenerateCreditEvaluationReportUseCase.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/report/CreditEvaluationReportGenerator.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/report/PdfCreditEvaluationReportGenerator.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/config/ApplicationConfiguration.kt
- Modelo: gpt-5.4
- Esforço: xalto
- Notas: adaptar recursos externos ao modelo consolidado e manter transação, serialização, filtros e PDF.

## T-034 — Separar tipos e padronizar KDocs em português [pendente]

- Refs: US-024, AC-082, AC-083, AC-084, US-026, AC-088
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/CreditFlowApplication.kt, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/domain, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application, src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure
- Modelo: gpt-5.4
- Esforço: xalto
- Notas: um tipo público por arquivo; KDoc em português em tipos e membros públicos/internos; comentários privados apenas quando explicarem decisões não óbvias.

## T-035 — Atualizar documentação e provar compatibilidade [pendente]

- Refs: US-026, AC-088, AC-089
- Arquivos: docs/architecture.md, docs/adrs/001-modular-monolith.md, src/test/kotlin, test/kotlin-architecture.test.mjs, .spec/verification/arquitetura-kotlin.json
- Modelo: gpt-5.4
- Esforço: alto
- Notas: atualizar imports dos testes, executar compilação, análise estática, suítes completas, verify e audit sem reduzir cobertura.
