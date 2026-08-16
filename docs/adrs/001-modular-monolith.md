# ADR-001 — Monólito modular

- Status: aceito
- Data: 2026-08-16

## Decisão

Implementar um deploy único com módulos de domínio, aplicação e infraestrutura. Regras e cálculo permanecem livres de frameworks; integrações dependem de portas da aplicação.

## Trade-offs

Ganhamos transações locais, depuração simples e menor custo operacional. Aceitamos escalar o conjunto e disciplinar fronteiras por testes. Um módulo só será extraído quando houver necessidade independente de escala, ciclo de mudança ou isolamento.
