# ADR-006 — Aurora como fonte de verdade; DynamoDB como evolução seletiva

- Status: proposto para produção
- Data: 2026-08-16

## Decisão

Usar Aurora PostgreSQL para avaliações, idempotência e outbox, preservando constraints e atomicidade. Considerar DynamoDB para projeções de leitura ou idempotência de altíssimo volume após medição.

## Trade-offs

Aurora oferece transações e consultas flexíveis, com scaling menos granular que DynamoDB. DynamoDB oferece escala e latência previsíveis, mas exigiria modelagem por padrão de acesso e complicaria a atomicidade do fluxo principal.
