# ADR-001 — Monólito modular organizado por feature

- Status: aceito
- Data: 2026-08-16

## Decisão

Manter um único deploy Spring Boot e organizar o fluxo principal em `evaluation/domain`, `evaluation/application` e `evaluation/infrastructure`. O adaptador HTTP separa `web/controller`, `web/dto`, `web/mapper` e `web/error`.

A aplicação usa regras, cálculos e modelos do domínio diretamente. Portas representam apenas recursos externos, como PostgreSQL, idempotência, observabilidade e geração de PDF. Segurança, configuração, mensageria, outbox e saúde continuam como infraestrutura transversal.

Cada tipo público ou interno de nível superior possui arquivo próprio. Tipos e operações públicas ou internas recebem KDoc em português.

## Consequências

Ganhamos transações locais, navegação previsível, depuração simples e menor custo operacional. Controllers ficam limitados à tradução HTTP e à chamada de casos de uso; serialização e persistência permanecem nos adaptadores.

Aceitamos escalar o conjunto e proteger as fronteiras por testes estruturais. Um módulo só será extraído quando houver necessidade independente de escala, ciclo de mudança ou isolamento.
