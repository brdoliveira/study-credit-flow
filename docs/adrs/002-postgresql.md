# ADR-002 — PostgreSQL no desenvolvimento e na fonte de verdade

- Status: aceito
- Data: 2026-08-16

## Decisão

Usar PostgreSQL 16 em container local e testes de integração, sem substituir o banco por H2. Flyway versiona o schema. Na AWS, a evolução natural é Aurora PostgreSQL.

## Trade-offs

Há maior custo de inicialização dos testes, mas o comportamento de constraints, concorrência e SQL é representativo. Aurora preserva o modelo transacional e reduz risco de migração.
