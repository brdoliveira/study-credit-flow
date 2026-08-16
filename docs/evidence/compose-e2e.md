# Prova ponta a ponta do Docker Compose

`./scripts/e2e-compose.ps1` cria uma pilha descartável com volumes limpos, gera o JAR, aguarda app, PostgreSQL, Keycloak e Redpanda ficarem saudáveis e executa `test/e2e/credit-flow.spec.mjs`.

A prova autentica `credit-writer` pelo OIDC no navegador, cria e consulta uma avaliação, baixa o PDF, consulta a Outbox, lê o evento no broker e confirma o processamento idempotente. O issuer continua público (`localhost`) enquanto JWKS, token e user-info usam o backchannel interno do Compose.

Antes de executar, copie `.env.example` para `.env` e substitua os placeholders por senhas locais. O script sempre encerra a pilha com `docker compose down --volumes`; o documento descreve o procedimento e não afirma uma execução inexistente.
