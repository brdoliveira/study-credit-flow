# Tasks: Neutralização de marca

> feature: neutralizacao-marca

## T-028 — Neutralizar identidade e proteger contra regressão [em-andamento]

- Refs: US-023, AC-080, AC-081, ASM-016, Q-006
- Arquivos: build.gradle.kts, SPEC_Credito_Rotativo.md, test/brand-neutrality.test.mjs, .spec/features/neutralizacao-marca/spec.md, .spec/features/neutralizacao-marca/tasks.md
- Notas: substituir o namespace pelo perfil público, atualizar referências históricas da spec, remover o PDF original e verificar toda a árvore publicável.

## T-029 — Simplificar referências internas do namespace [em-andamento]

- Refs: US-023, AC-081
- Arquivos: src/main/kotlin/io/github/brdoliveira/creditflow/platform/config/ApplicationConfiguration.kt, src/test/kotlin/io/github/brdoliveira/creditflow/platform/security/ApiSecurityTest.kt, test/brand-neutrality.test.mjs, .spec/features/neutralizacao-marca/tasks.md
- Notas: usar aliases apenas para tipos homônimos e imports normais para os demais tipos internos.
