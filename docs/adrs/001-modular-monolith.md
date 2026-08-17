# ADR-001 — Monólito modular organizado por feature

- Status: aceito
- Data: 2026-08-16

## Decisão

Manter um único deploy Spring Boot e organizar o fluxo principal em `evaluation/domain`, `evaluation/application` e `evaluation/infrastructure`. Eventos de aplicação ficam em `evaluation/application/event`; o adaptador HTTP separa `web/controller`, `web/dto`, `web/mapper` e `web/error`.

A aplicação usa regras, cálculos e modelos do domínio diretamente. Portas representam apenas recursos externos, como PostgreSQL, idempotência, observabilidade e geração de PDF. Mensageria, outbox, idempotência e métricas específicas de crédito ficam na infraestrutura de `evaluation`; segurança, configuração, saúde e correlação continuam transversais no pacote `creditflow.platform`. A criação da outbox é explícita na persistência e serializa `CreditEvaluationCompleted`, mantendo a classe Kotlin como fonte única do contrato do evento.

Cada tipo público ou interno de nível superior possui arquivo próprio. Tipos e operações públicas ou internas recebem KDoc em português.

Ferramentas de engenharia que não participam do runtime, como o gerador do relatório de carga, permanecem em subprojetos sob `tools` e não entram no JAR executável da aplicação.

## Consequências

Ganhamos transações locais, navegação previsível, depuração simples e menor custo operacional. Controllers ficam limitados à tradução HTTP e à chamada de casos de uso; serialização e persistência permanecem nos adaptadores.

Aceitamos escalar o conjunto e proteger as fronteiras por testes estruturais. Um módulo só será extraído quando houver necessidade independente de escala, ciclo de mudança ou isolamento.
