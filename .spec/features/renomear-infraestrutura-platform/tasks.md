# Tasks: Renomear infraestrutura compartilhada para platform

> feature: renomear-infraestrutura-platform

## T-041 — Renomear o namespace compartilhado e preservar contratos [pendente]

- Refs: US-030, AC-096
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/platform, src/test/kotlin/io/github/brdoliveira/creditflow/platform, test/kotlin-architecture.test.mjs, docs/architecture.md, docs/adrs/001-modular-monolith.md, .spec/features
- Notas: mover somente o pacote global `creditflow.infrastructure`; manter `creditflow.evaluation.infrastructure` e atualizar a rastreabilidade histórica dos arquivos movidos.
