# Design: Arquitetura Kotlin

> feature: arquitetura-kotlin

## Direção

O sistema continua sendo um único deploy Spring Boot, organizado pela feature de avaliação. Segurança, configuração, saúde, observabilidade, mensageria e outbox permanecem como infraestrutura transversal.

```text
creditflow
├── evaluation
│   ├── domain
│   │   ├── calculation
│   │   └── rule
│   ├── application
│   │   ├── port
│   │   └── report
│   └── infrastructure
│       ├── idempotency
│       ├── persistence
│       ├── report
│       └── web
│           ├── controller
│           ├── dto
│           ├── error
│           └── mapper
├── application/event
└── infrastructure
    ├── config
    ├── health
    ├── messaging
    ├── observability
    ├── outbox
    ├── privacy
    ├── security
    └── web
```

## Dependências

```text
controller → caso de uso → domínio
                       ↓
                portas de saída
                       ↑
          PostgreSQL / idempotência / PDF
```

- O domínio não importa Spring, JPA, Jackson, HTTP ou mensageria.
- A aplicação importa o domínio diretamente.
- Portas representam somente recursos externos.
- Controllers conhecem DTOs, mapeadores e casos de uso, mas não repositórios ou serializadores.
- Adaptadores convertem modelos somente em suas fronteiras.

## Modelo consolidado

`CreditEvaluation` é o registro imutável e tipado produzido pelo caso de uso e persistido pela porta. Ele reutiliza `CreditDecisionStatus`, `RuleResult`, `RuleSeverity` e `RuleStatus` do domínio. JSON de regras, JPA e resposta idempotente são detalhes dos adaptadores PostgreSQL.

O `RuleEngine` consolida a decisão. `EvaluateRevolvingCreditUseCase` executa diretamente o motor e o `CreditLimitCalculator`, mascara o CPF, monta a avaliação auditável e solicita sua persistência.

## Casos de uso

- `EvaluateRevolvingCreditUseCase`: executa regras, cálculo e persistência.
- `CreateCreditEvaluationUseCase`: aplica idempotência e observabilidade à criação.
- `FindCreditEvaluationUseCase`: consulta uma avaliação por identificador.
- `ListCreditEvaluationsUseCase`: aplica filtros, paginação e ordenação.
- `GenerateCreditEvaluationReportUseCase`: consulta todas as páginas e aciona a porta de PDF.

## Organização dos arquivos

Cada tipo público ou interno de nível superior tem arquivo próprio e de mesmo nome. Tipos privados estritamente auxiliares podem permanecer coesos com o tipo principal.

Tipos e operações públicas ou internas têm KDoc em português. Propriedades de modelos são explicadas no KDoc do tipo com `@property` quando carregam semântica de negócio. Auxiliares privados triviais não recebem comentários que apenas repetiriam o código.

## Compatibilidade

Endpoints, payloads, status HTTP, escopos, métricas, schema PostgreSQL, evento, conteúdo do PDF e frontend permanecem inalterados. Os testes existentes mudam apenas para acompanhar os pacotes e o modelo consolidado.
