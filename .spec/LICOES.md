# LIÇÕES — mantido pelo motor (`onp-spec licoes`)

> Não edite à mão: qualquer escrita do motor sobrescreve este arquivo.
> Estado canônico em `.spec/licoes.json`; mutação só via `onp-spec licoes`.

## Confirmadas — carregue no Especificar/Projetar

Corroboradas em múltiplas features. Aplique como guia.

### L-004 — Após mudar código ou testes compartilhados, renove o verify de todas as features antes do audit --ci, pois as provas anteriores ficam obsoletas.
- sinal: `VERIFY_OBSOLETO` · recorrência: 2 feature(s) · penalidades: 0
- features: arquitetura-kotlin, credito-rotativo
- última evidência: — (credito-rotativo, 2026-08-17T06:30:52.587Z)

## Candidatas — em observação, NÃO aplicar ainda

Vistas em uma feature só. Registradas, não confiadas.

### L-001 — Ao mover pacotes, atualizar no mesmo commit os caminhos das tarefas históricas e mapear explicitamente cada novo arquivo para preservar a rastreabilidade.
- sinal: `ARQUIVO_INEXISTENTE` · recorrência: 1 feature(s) · penalidades: 0
- features: arquitetura-kotlin
- última evidência: T-031 (arquitetura-kotlin, 2026-08-17T02:47:52.913Z)

### L-002 — Validar a disponibilidade do Docker antes do verify oficial, pois a falha precoce dos Testcontainers impede o runner encadeado de analisar até os testes independentes que passaram.
- sinal: `AC_SEM_PROVA` · recorrência: 1 feature(s) · penalidades: 0
- features: arquitetura-kotlin
- última evidência: AC-088 (arquitetura-kotlin, 2026-08-17T02:47:53.185Z)

### L-003 — Antes de renomear um namespace arquitetural, criar um teste estrutural anotado que falhe com o nome atual e proteja o destino.
- sinal: `AC_SEM_TESTE` · recorrência: 1 feature(s) · escopo: `arquitetura` · penalidades: 0
- features: renomear-infraestrutura-platform
- última evidência: AC-096 (renomear-infraestrutura-platform, 2026-08-17T05:31:27.431Z)

## Quarentena — aplicadas e falharam, ignorar

A falha recorreu mesmo com a lição aplicada. Revisão é do usuário.

_nenhuma_
