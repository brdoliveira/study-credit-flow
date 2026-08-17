# Arquitetura

## Organização atual

A solução é um monólito modular Spring Boot organizado pela feature principal de avaliação. As dependências de negócio apontam para dentro:

```text
creditflow
├── evaluation
│   ├── domain
│   │   ├── calculation
│   │   └── rule
│   ├── application
│   │   ├── port
│   │   ├── event
│   │   └── report
│   └── infrastructure
│       ├── idempotency
│       ├── messaging
│       ├── observability
│       ├── outbox
│       ├── persistence
│       ├── report
│       └── web
│           ├── controller
│           ├── dto
│           ├── error
│           └── mapper
└── platform
    ├── config
    ├── health
    ├── observability (correlação)
    ├── privacy
    ├── security
    └── web
```

Os caminhos de referência são `evaluation/domain`, `evaluation/application`, `evaluation/application/event`, `evaluation/infrastructure` e `creditflow/platform`. Idempotência, mensageria, outbox e métricas de crédito pertencem à feature — em especial `evaluation/infrastructure/messaging` e `evaluation/infrastructure/outbox`; bootstrap Spring, segurança, saúde e correlação ficam em `platform`. Os testes de domínio e aplicação espelham os pacotes de produção, enquanto as integrações transversais preservam seus agrupamentos de contrato. Cada tipo público ou interno de nível superior fica em um arquivo próprio e de mesmo nome.

O domínio contém modelos, regras e cálculo sem Spring, JPA ou Jackson. A aplicação usa o domínio diretamente e define portas somente para recursos externos: persistência, idempotência, métricas e geração do PDF. Os adaptadores da feature implementam HTTP, PostgreSQL, relatório, mensageria e outbox. Bootstrap Spring, segurança, saúde e correlação permanecem transversais sob `platform`.

## Fluxo da avaliação

```text
controller → caso de uso → domínio
                       ↓
                portas de saída
                       ↑
          PostgreSQL / idempotência / PDF
```

O controller valida o contrato HTTP e converte DTOs por meio do mapper. `CreateCreditEvaluationUseCase` coordena idempotência e métricas; `EvaluateRevolvingCreditUseCase` executa diretamente `RuleEngine` e `CreditLimitCalculator`, monta `CreditEvaluation` e solicita sua persistência. No mesmo limite transacional, o adaptador PostgreSQL cria a entrada da outbox serializando `CreditEvaluationCompleted`; o contrato do evento fica definido pela classe Kotlin, e não por uma segunda estrutura em SQL. Os controllers não acessam repositórios, serializadores nem geradores de relatório.

O CPF completo existe apenas durante o processamento necessário. Persistência, eventos, relatórios e respostas usam identificação mascarada. O modelo tipado do domínio é compartilhado com a aplicação; JSON e JPA ficam restritos aos adaptadores.

## Controles operacionais

- OIDC/JWT e autorização por escopo;
- HTTPS obrigatório no perfil de produção;
- idempotência com retenção de 24 horas e detecção de payload divergente;
- transactional outbox para evitar dual write;
- correlação em resposta, MDC, decisão e evento;
- métricas sem CPF ou `evaluationId` como tag;
- liveness do processo separada da readiness de dependências;
- erros técnicos sem stack trace no contrato HTTP.

## Evolução para AWS

```mermaid
flowchart LR
    U[Canal corporativo] --> IDP[IdP corporativo]
    U --> WAF[AWS WAF / API Gateway ou ALB]
    WAF --> ECS[ECS Fargate em sub-redes privadas]
    ECS --> AUR[(Aurora PostgreSQL Multi-AZ)]
    ECS --> MSK[MSK ou EventBridge]
    ECS --> SM[Secrets Manager + KMS]
    ECS --> CW[CloudWatch / OpenTelemetry]
    MSK --> C[Consumidores idempotentes]
```

ECS/Fargate reduz a carga operacional sem impedir uma migração posterior para EKS. Aurora preserva a consistência transacional de avaliação, idempotência e outbox. DynamoDB permanece uma opção futura para projeções ou idempotência de volume muito alto.

## Trade-offs e limites

O monólito modular prioriza consistência e velocidade de evolução. Separar serviços cedo adicionaria transações distribuídas sem uma fronteira de escala comprovada. Publicação e relatórios são candidatos naturais a extração futura, preservando eventos versionados.
