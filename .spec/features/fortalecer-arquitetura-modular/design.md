# Design: Fortalecer arquitetura modular

## Direção das dependências

```text
evaluation/domain
        ↑
evaluation/application ← portas
        ↑
evaluation/infrastructure
        ↑
bootstrap e adaptadores de entrada
```

- `evaluation/domain` depende somente do próprio domínio e da biblioteca padrão.
- `evaluation/application` depende somente do domínio, de seus próprios tipos e de suas portas.
- `evaluation/infrastructure` implementa as portas e pode depender das duas camadas internas.
- Infraestrutura transversal não pode ser importada pelo domínio ou pela aplicação.

## Organização de pacotes

Os componentes nomeados em torno de `CreditEvaluation` passam a pertencer à feature:

```text
creditflow/evaluation
├── domain
├── application
│   ├── event
│   ├── port
│   └── report
└── infrastructure
    ├── idempotency
    ├── messaging
    ├── observability
    ├── outbox
    ├── persistence
    ├── report
    └── web
```

Configuração de composição, segurança, health, correlação HTTP e sessão continuam transversais porque atendem ao processo inteiro.

## Filtros internos

`CreditEvaluationSearchCriteria` continua representando texto HTTP e valida os valores públicos. Depois da validação, converte `decision` para `CreditDecisionStatus?`. `CreditEvaluationFilter` e `CreditEvaluationRepository` trabalham apenas com o enum.

## Outbox

O trigger existente é removido por uma nova migração, sem reescrever migrações já publicadas. `PostgresCreditEvaluationRepository`, dentro da mesma transação de `save`, persiste a avaliação, força o flush necessário à chave estrangeira e insere a outbox serializando `CreditEvaluationCompleted` pelo `ObjectMapper`. A classe Kotlin passa a ser a única definição executável do payload.

## Prova

Os testes estruturais percorrem todos os imports Kotlin das camadas internas, validam que os diretórios inspecionados não estão vazios e verificam a organização de produção e testes. Testes funcionais existentes protegem HTTP, persistência e mensageria; novos títulos recebem as marcações dos critérios de aceite desta feature.

## Riscos e mitigação

- A movimentação de pacotes pode deixar imports antigos: a compilação e um teste estrutural de namespaces legados bloqueiam isso.
- A troca do trigger por inserção explícita pode perder atomicidade: o teste PostgreSQL cobre commit e rollback da avaliação com a outbox.
- O filtro tipado pode alterar o contrato HTTP: os testes de controller mantêm os valores públicos e erros atuais.
