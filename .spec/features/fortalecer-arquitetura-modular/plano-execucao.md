# Plano de execução — fortalecer-arquitetura-modular

> gerado por `onp-spec plano` em 2026-08-17 03:30 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano fortalecer-arquitetura-modular`

## Resumo — o que vai acontecer

- **5 tarefa(s) pendente(s)**: 5 em 2 faixa(s) paralela(s) + 0 sequencial(is)
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano fortalecer-arquitetura-modular --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/fortalecer-arquitetura-modular`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2

#### faixa-1 — branch `spec/fortalecer-arquitetura-modular-faixa-1` — worktree `../onp-worktrees/study-credit-flow-fortalecer-arquitetura-modular-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-036 | Impor todas as fronteiras arquiteturais | `gpt-5.6-terra` | high | `test/kotlin-architecture.test.mjs`, `src/test/kotlin/io/github/brdoliveira/creditflow/spec/SpecificationContractTest.kt` |
| T-037 | Consolidar a organização por feature | `gpt-5.6-terra` | high | `src/main/kotlin/io/github/brdoliveira/creditflow/application/event/CreditEvaluationCompleted.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/idempotency/CanonicalRequestHasher.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/CreditMetrics.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox`, `src/test/kotlin/io/github/brdoliveira/creditflow/domain`, `src/test/kotlin/io/github/brdoliveira/creditflow/application`, `src/test/kotlin/io/github/brdoliveira/creditflow/evaluation`, `docs/architecture.md` |
| T-040 | Atualizar documentação e provar compatibilidade | `gpt-5.6-terra` | medium | `docs/architecture.md`, `docs/adrs/001-modular-monolith.md`, `test/kotlin-architecture.test.mjs`, `src/test/kotlin`, `test` |

#### faixa-2 — branch `spec/fortalecer-arquitetura-modular-faixa-2` — worktree `../onp-worktrees/study-credit-flow-fortalecer-arquitetura-modular-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-038 | Tipar o filtro de decisão de ponta a ponta | `gpt-5.6-terra` | medium | `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/CreditEvaluationFilter.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/dto/CreditEvaluationSearchCriteria.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/CreditEvaluationController.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/CreditEvaluationReportController.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/PostgresCreditEvaluationRepository.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationControllerTest.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/persistence/PostgresCreditEvaluationRepositoryIT.kt` |
| T-039 | Tornar a classe Kotlin a fonte do evento da outbox | `gpt-5.6-terra` | high | `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/application/event/CreditEvaluationCompleted.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/PostgresCreditEvaluationRepository.kt`, `src/main/resources/db/migration/V5__explicit_credit_outbox.sql`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationMessagingIT.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/OutboxKafkaIT.kt` |

## Gestão de branches e commits

1. branch de trabalho `spec/fortalecer-arquitetura-modular` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify fortalecer-arquitetura-modular` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Codex headless (codex exec)

```bash
bash .spec/features/fortalecer-arquitetura-modular/executar-tarefas.sh
```

Cada faixa roda `codex exec` com **janela de contexto limpa**, no seu worktree, com
`--model` e `model_reasoning_effort` já definidos por tarefa e sandbox `workspace-write`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/study-credit-flow-fortalecer-arquitetura-modular-logs/`.

**Confirmação de custos — antes de executar**: os modelos e esforços por
tarefa estão nas tabelas acima; o agente CONFIRMA com o usuário se estão
dentro da licença/cota dele (modelo forte + esforço alto torra tokens).
Para gastar menos: `onp-spec plano fortalecer-arquitetura-modular --modelo gpt-5.6-luna --esforco baixo`
(tudo) ou por tarefa `onp-spec tarefa fortalecer-arquitetura-modular T-xxx --modelo <m> --esforco <nível>` — e regenere o plano.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo fortalecer-arquitetura-modular --tabela   # a tabela de andamento
onp-spec resumo fortalecer-arquitetura-modular            # o resumo em texto
```

