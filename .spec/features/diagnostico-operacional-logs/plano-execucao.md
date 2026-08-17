# Plano de execução — diagnostico-operacional-logs

> gerado por `onp-spec plano` em 2026-08-17 05:48 — NÃO edite à mão;
> mudou tasks.md ou a config? Regenere: `onp-spec plano diagnostico-operacional-logs`

## Resumo — o que vai acontecer

- **4 tarefa(s) pendente(s)**: 4 em 4 faixa(s) paralela(s) + 0 sequencial(is)
- **1 faixa = 1 worktree + 1 branch + 1 janela de contexto limpa** — faixas não compartilham nenhum arquivo entre si
- prefere outra seleção ou uma após a outra? Regenere com `onp-spec plano diagnostico-operacional-logs --paralelizar T-xxx,T-yyy` ou `--sequencial`
- tudo acontece na branch de trabalho `spec/diagnostico-operacional-logs`; levar para a main é decisão sua

## Faixas e ondas

### Onda 1 — faixa-1 ∥ faixa-2 ∥ faixa-3

#### faixa-1 — branch `spec/diagnostico-operacional-logs-faixa-1` — worktree `../onp-worktrees/study-credit-flow-diagnostico-operacional-logs-faixa-1`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-042 | Criar fundação segura de logging estruturado | `gpt-5.6-terra` | high | `src/main/resources/logback-spring.xml`, `src/main/resources/application-observability.yml`, `src/test/kotlin/io/github/brdoliveira/creditflow/platform/observability/StructuredLoggingIT.kt` |

#### faixa-2 — branch `spec/diagnostico-operacional-logs-faixa-2` — worktree `../onp-worktrees/study-credit-flow-diagnostico-operacional-logs-faixa-2`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-043 | Registrar falhas HTTP técnicas | `gpt-5.6-terra` | medium | `src/main/kotlin/io/github/brdoliveira/creditflow/platform/observability/SafeExceptionDetails.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/error/GlobalExceptionHandler.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/observability/ObservabilityTest.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/observability/RuntimeObservabilityIT.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/web/error/GlobalExceptionLoggingTest.kt` |

#### faixa-3 — branch `spec/diagnostico-operacional-logs-faixa-3` — worktree `../onp-worktrees/study-credit-flow-diagnostico-operacional-logs-faixa-3`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-044 | Propagar e registrar contexto assíncrono | `gpt-5.6-terra` | high | `src/main/kotlin/io/github/brdoliveira/creditflow/platform/observability/CorrelationLogContext.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/outbox/OutboxPublisher.kt`, `src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/messaging/CreditEvaluationKafkaListener.kt`, `src/test/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/messaging/AsyncOperationalLoggingTest.kt` |

### Onda 2 — faixa-4

#### faixa-4 — branch `spec/diagnostico-operacional-logs-faixa-4` — worktree `../onp-worktrees/study-credit-flow-diagnostico-operacional-logs-faixa-4`

| tarefa | título | modelo | esforço | arquivos |
|---|---|---|---|---|
| T-045 | Documentar operação e provar compatibilidade | `gpt-5.6-terra` | medium | `README.md`, `docs/architecture.md`, `.env.example`, `test/kotlin-architecture.test.mjs`, `.spec/verification/diagnostico-operacional-logs.json` |

## Gestão de branches e commits

1. branch de trabalho `spec/diagnostico-operacional-logs` criada do ponto atual (se ainda não existir)
2. cada faixa nasce dela como branch própria e roda no seu worktree — **1 tarefa = 1 commit** (`T-xxx feature: título`)
3. terminou a onda → merge `--no-ff` de cada faixa de volta, na ordem; conflito interrompe a faixa e pede resolução humana
4. faixa mesclada → worktree removido, branch apagada, tarefa marcada `[concluida]` no tasks.md
5. gate final na branch de trabalho: `onp-spec verify diagnostico-operacional-logs` + `onp-spec audit --ci` — **exit 0 ou não está pronto**

## Como executar

### ▶ Execução — Codex headless (codex exec)

```bash
bash .spec/features/diagnostico-operacional-logs/executar-tarefas.sh
```

Cada faixa roda `codex exec` com **janela de contexto limpa**, no seu worktree, com
`--model` e `model_reasoning_effort` já definidos por tarefa e sandbox `workspace-write`. Os prompts exatos estão
embutidos no script — quer rodar uma faixa na mão, é só copiá-los de lá.
Logs: `../onp-worktrees/study-credit-flow-diagnostico-operacional-logs-logs/`.

**Confirmação de custos — antes de executar**: os modelos e esforços por
tarefa estão nas tabelas acima; o agente CONFIRMA com o usuário se estão
dentro da licença/cota dele (modelo forte + esforço alto torra tokens).
Para gastar menos: `onp-spec plano diagnostico-operacional-logs --modelo gpt-5.6-luna --esforco baixo`
(tudo) ou por tarefa `onp-spec tarefa diagnostico-operacional-logs T-xxx --modelo <m> --esforco <nível>` — e regenere o plano.

### 📣 Acompanhamento — tabela + resumo no chat (a cada 1 min)

O script roda em **background**: o agente AVISA o usuário antes de iniciar e,
enquanto roda, posta no chat a cada ~1 minuto a **tabela de andamento** (qual
tarefa está rodando, qual não está, o que concluiu/falhou) junto com o
**resumo geral de andamento** (escrito por IA; sem IA, o motor resume). Ao
final, o usuário recebe o resumo completo da execução. A qualquer momento:

```bash
onp-spec resumo diagnostico-operacional-logs --tabela   # a tabela de andamento
onp-spec resumo diagnostico-operacional-logs            # o resumo em texto
```

