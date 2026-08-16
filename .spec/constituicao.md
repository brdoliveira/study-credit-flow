# Constituição — v1.1.0

<!--
  Princípios inegociáveis do projeto. Não são estilo: são restrições.
  P-xxx = princípio (código de rastreio, como US/AC/T).
  Níveis: [DEVE] obrigatório · [RECOMENDADO] forte · [PODE] permitido/explícito.
  Todo [DEVE] precisa de verificação executável — senão o audit acusa
  "princípio sem verificação" (PRINCIPIO_SEM_VERIFICACAO). Formatos:
    - verificação(gate): satisfeita pelo próprio audit (só p/ princípios "meta")
    - verificação(teste): @principle:P-xxx
    - verificação(proibido): `regex` em `glob`
    - verificação(obrigatório): `regex` em `glob`
-->

## P-001 [DEVE] Todo requisito tem prova executável

Nenhuma feature é declarada pronta sem o audit em modo CI sair limpo (exit 0).
Este princípio é verificado pelo próprio mecanismo do audit (AC_SEM_TESTE,
AC_SEM_PROVA, TASK_CONCLUIDA_SEM_PROVA) — não precisa de teste extra seu.

- verificação(gate): intrínseca ao audit

## P-002 [DEVE] Segredos nunca em código

Chaves e senhas vêm de variáveis de ambiente, nunca hard-coded.

- verificação(teste): @principle:P-002

## P-003 [DEVE] O domínio não depende de frameworks

As regras e os modelos de negócio não importam Spring nem APIs de persistência.

- verificação(teste): @principle:P-003

## P-004 [DEVE] Integração usa PostgreSQL real

Testes de persistência e concorrência não usam H2; o comportamento é validado com PostgreSQL/Testcontainers.

- verificação(teste): @principle:P-004
