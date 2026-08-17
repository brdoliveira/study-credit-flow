# ADR-003 — Transactional outbox e consumidores idempotentes

- Status: aceito
- Data: 2026-08-16

## Decisão

Persistir avaliação e evento de outbox atomicamente no PostgreSQL. Um publicador envia eventos versionados ao Kafka com retry limitado; consumidores registram `eventId` antes de aplicar o efeito.

## Trade-offs

Eliminamos a janela de dual write, mas aceitamos consistência eventual, tabela adicional e possibilidade de entrega duplicada. Monitoramento de backlog e idempotência do consumidor são obrigatórios.
