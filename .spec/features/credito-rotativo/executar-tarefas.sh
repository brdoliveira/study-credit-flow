#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano credito-rotativo` em 2026-08-15 21:37
# NÃO edite à mão: mudou tasks.md ou a config, regenere o plano.
#
# uso:
#   bash executar-tarefas.sh                  tudo (ondas → sequenciais → gate)
#   bash executar-tarefas.sh --faixa <id>     reexecuta UMA faixa (+ merge + gate)
#   bash executar-tarefas.sh --seq <T-xxx>    reexecuta UMA tarefa sequencial
#   bash executar-tarefas.sh --gate           só o gate (verify + audit)
#   bash executar-tarefas.sh --listar         mostra faixas, tarefas e estados
#   (acrescente --sem-gate para não rodar o gate ao final)
#
# resumo do que está rolando, a qualquer momento: onp-spec resumo credito-rotativo
set -u
set -o pipefail

RUN_ID='study-credit-flow-credito-rotativo-msuwcrgs'
FEATURE='credito-rotativo'
BASE_BRANCH='spec/credito-rotativo'
ENGINE='C:\Users\brufe\.agents\skills\onp-spec-driven\scripts\onp-spec.mjs'
CODEX_FLAGS=(--sandbox 'workspace-write')
STREAM_FLAGS=(--json)
FALHAS=""
COM_GATE=1
RESUMO_MODEL='gpt-5.6-luna'
RESUMO_PID=""

verde()    { printf '\033[32m%s\033[0m\n' "$*"; }
vermelho() { printf '\033[31m%s\033[0m\n' "$*"; }
amarelo()  { printf '\033[33m%s\033[0m\n' "$*"; }
info()     { printf '· %s\n' "$*"; }
falhar()   { vermelho "✘ $*"; exit 1; }

# eventos vão para o ledger GLOBAL (~/.onp-spec/painel/ledger.jsonl):
# um arquivo para todos os projetos, é o que o onp-spec resumo lê
evento() { node "$ENGINE" evento --run "$RUN_ID" "$@" >/dev/null 2>&1 || true; }

# ── ambiente (todos os modos passam por aqui) ────────────────────────
preparar_ambiente() {
  command -v git >/dev/null 2>&1 || falhar "git não encontrado"
  command -v node >/dev/null 2>&1 || falhar "node não encontrado"
  command -v codex >/dev/null 2>&1 || falhar "Codex CLI (codex) não encontrado — instale-o ou siga o modo manual em plano-execucao.md"
  TOPLEVEL=$(git rev-parse --show-toplevel 2>/dev/null) || falhar "fora de um repositório git"
  cd "$TOPLEVEL" || exit 1
  # artefatos recém-gerados pelo `onp-spec plano` são sujeira esperada:
  # se forem a ÚNICA sujeira, o script mesmo commita; qualquer outra, aborta
  if [ -n "$(git status --porcelain)" ]; then
    if [ -z "$(git status --porcelain | grep -v -e 'plano-execucao\.' -e 'plano\.json' -e 'executar-tarefas\.sh')" ]; then
      git add -A
      git commit -q -m "plano de execução: $FEATURE (artefatos gerados)"
      info "artefatos do plano commitados"
    else
      falhar "árvore suja além dos artefatos do plano — commite ou faça git stash antes (os worktrees partem do último commit)"
    fi
  fi
  git ls-files --error-unmatch -- '.spec/features/credito-rotativo/spec.md' >/dev/null 2>&1 || falhar "spec.md não está commitada — os worktrees das faixas precisam dela no git"
  ATUAL=$(git rev-parse --abbrev-ref HEAD)
  [ "$ATUAL" != "HEAD" ] || falhar "HEAD destacado — troque para uma branch"
  if [ "$ATUAL" != "$BASE_BRANCH" ]; then
    if git show-ref --verify --quiet "refs/heads/$BASE_BRANCH"; then
      git checkout -q "$BASE_BRANCH" || falhar "não consegui trocar para $BASE_BRANCH"
    else
      git checkout -q -b "$BASE_BRANCH" || falhar "não consegui criar $BASE_BRANCH"
    fi
    info "branch de trabalho: $BASE_BRANCH (a partir de $ATUAL)"
  fi
  git worktree prune
  LOG_DIR="$(dirname "$TOPLEVEL")/onp-worktrees/study-credit-flow-credito-rotativo-logs"
  WT_BASE="$(dirname "$TOPLEVEL")/onp-worktrees/study-credit-flow-credito-rotativo"
  STREAMS_DIR="${ONP_SPEC_HOME:-$HOME/.onp-spec}/painel/streams/$RUN_ID"
  mkdir -p "$LOG_DIR" "$STREAMS_DIR"
}

# worktree limpo mesmo depois de uma tentativa que falhou
preparar_worktree() { # $1=faixa $2=branch $3=worktree
  git worktree prune
  if [ -e "$3" ]; then git worktree remove --force "$3" >/dev/null 2>&1; rm -rf "$3"; fi
  if git show-ref --verify --quiet "refs/heads/$2"; then git branch -D "$2" >/dev/null 2>&1; fi
  git worktree add "$3" -b "$2" >/dev/null 2>&1 || { vermelho "✘ não consegui criar o worktree de $1 em $3"; return 1; }
}

tentativa() { # $1=faixa — conta reexecuções (vai para o ledger)
  local arq="$LOG_DIR/.tentativa-$1"
  local n=1
  [ -f "$arq" ] && n=$(( $(cat "$arq") + 1 ))
  printf "%s" "$n" > "$arq"
  printf "%s" "$n"
}

# uma tarefa = uma sessão codex exec headless com contexto limpo.
# o JSONL da sessão vira o stream da tarefa no ledger
rodar_tarefa() { # $1=escopo(faixa|seq) $2=T-xxx $3=prompt $4=modelo $5=esforço
  local chave="$1--$2"
  local stream="$STREAMS_DIR/$chave.jsonl"
  evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado executando --stream "$chave"
  info "$2 — codex exec ($4 · $5) · stream: $chave"
  # --add-dir: o .git compartilhado dos worktrees mora no repo principal —
  # sem ele o sandbox workspace-write bloquearia o commit da tarefa
  if codex exec "$3" --model "$4" -c model_reasoning_effort="$5" "${STREAM_FLAGS[@]}" "${CODEX_FLAGS[@]}" --add-dir "$TOPLEVEL" > "$stream" 2>>"$LOG_DIR/$1.log"; then
    evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado concluida --stream "$chave"
    node "$ENGINE" stream-resumo "$RUN_ID" "$chave" 2>/dev/null || true
    return 0
  fi
  evento --tipo tarefa --tarefa "$2" --faixa "$1" --estado falhou --stream "$chave"
  node "$ENGINE" stream-resumo "$RUN_ID" "$chave" 2>/dev/null || true
  return 1
}

mesclar_faixa() { # $1=faixa $2=branch $3=worktree $4=exit-da-faixa
  if [ "$4" -ne 0 ]; then
    evento --tipo faixa --faixa "$1" --estado falhou
    vermelho "✘ $1 falhou (log: $LOG_DIR/$1.log) — worktree mantido para inspeção: $3"
    amarelo "  reexecute só ela: bash .spec/features/credito-rotativo/executar-tarefas.sh --faixa $1"
    FALHAS="$FALHAS $1"; return 1
  fi
  evento --tipo faixa --faixa "$1" --estado mesclando
  if git merge --no-ff "$2" -m "merge $1 ($FEATURE)"; then
    git worktree remove --force "$3" >/dev/null 2>&1
    git branch -d "$2" >/dev/null 2>&1
    evento --tipo faixa --faixa "$1" --estado mesclada
    verde "✔ $1 mesclada em $BASE_BRANCH"
  else
    git merge --abort >/dev/null 2>&1
    evento --tipo faixa --faixa "$1" --estado conflito
    vermelho "✘ conflito ao mesclar $1 — resolva na mão: git merge $2 (worktree mantido: $3)"
    FALHAS="$FALHAS $1"; return 1
  fi
}

marcar_concluidas() { # $@=T-xxx
  for t in "$@"; do node "$ENGINE" tarefa "$FEATURE" "$t" concluida >/dev/null || true; done
}

# ── resumo geral de andamento: 1/min enquanto a execução roda ─────────
# escrito por IA (codex exec somente leitura) com fallback do motor; vai
# para o terminal e para o ledger — o agente repassa o texto no chat.
gerar_resumo() {
  local ctx ia
  ctx=$(node "$ENGINE" resumo "$FEATURE" --contexto 2>/dev/null) || ctx=""
  [ -n "$ctx" ] || return 0
  ia=$(codex exec "Você narra, para o dono do produto, uma execução de tarefas de código em andamento. Estado mecânico:

$ctx

Escreva o RESUMO GERAL DE ANDAMENTO: um parágrafo único de 2 a 4 frases, em português simples, dizendo o que está acontecendo agora, o que já terminou, o que falhou e se o usuário precisa agir. Sem markdown, sem listas." --model "$RESUMO_MODEL" --sandbox read-only --ephemeral 2>/dev/null)
  if [ -n "$ia" ]; then
    node "$ENGINE" resumo "$FEATURE" --gravar --origem ia --texto "$ia" >/dev/null 2>&1 || true
    printf '\n📣 resumo (IA): %s\n' "$ia"
  else
    node "$ENGINE" resumo "$FEATURE" --gravar >/dev/null 2>&1 || true
    printf '\n📣 resumo: %s\n' "$(node "$ENGINE" resumo "$FEATURE" 2>/dev/null)"
  fi
}

# mata o loop E o sleep filho — senão o sleep herda o stdout e quem chamou
# o script via pipe fica esperando EOF por até 60s depois do exit
parar_resumos() {
  [ -n "$RESUMO_PID" ] || return 0
  command -v pkill >/dev/null 2>&1 && pkill -P "$RESUMO_PID" 2>/dev/null
  kill "$RESUMO_PID" 2>/dev/null
  RESUMO_PID=""
}

iniciar_resumos() {
  ( while :; do sleep 60; gerar_resumo; done ) &
  RESUMO_PID=$!
  # ao sair: para o loop e grava um último resumo (o estado final, do motor)
  trap 'parar_resumos; node "$ENGINE" resumo "$FEATURE" --gravar >/dev/null 2>&1 || true' EXIT
}

# ── faixa-1: T-001 ──
executar_faixa_1() {
  local WT="$WT_BASE-faixa-1"
  preparar_worktree 'faixa-1' 'spec/credito-rotativo-faixa-1' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-1' --estado executando --tentativa "$(tentativa 'faixa-1')"
  : > "$LOG_DIR/faixa-1.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-1' 'T-001' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-001 — "Preparar projeto Kotlin e testes de especificação"
  critérios/refs: AC-045 (Pipeline bloqueia mudança inválida), AC-047 (Documentação permite reprodução e defesa técnica)
  arquivos permitidos (e seus testes): settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties, onpspec.config.json, src/test/kotlin/io/github/brdoliveira/creditflow/spec/SpecificationContractTest.kt
  mensagem de commit: "T-001 credito-rotativo: Preparar projeto Kotlin e testes de especificação"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-1.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-1' 'spec/credito-rotativo-faixa-1' "$WT" "$st" || return 1
  marcar_concluidas T-001
  return 0
}

# ── faixa-2: T-002 ──
executar_faixa_2() {
  local WT="$WT_BASE-faixa-2"
  preparar_worktree 'faixa-2' 'spec/credito-rotativo-faixa-2' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-2' --estado executando --tentativa "$(tentativa 'faixa-2')"
  : > "$LOG_DIR/faixa-2.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-2' 'T-002' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-002 — "Implementar domínio e motor extensível de regras"
  critérios/refs: AC-004 (Todas as regras registradas são executadas), AC-005 (Score abaixo do mínimo reprova), AC-006 (Excesso de atrasos reprova), AC-007 (Ausência de limite disponível reprova), AC-008 (Comprometimento excessivo reprova), AC-009 (Tendência elevada gera alerta explicável), AC-010 (Nova regra não altera o orquestrador), AC-011 (Mesmas entradas geram a mesma decisão)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/domain/model/CreditEvaluationContext.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/model/RuleResult.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/model/CreditDecision.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/rule/CreditRule.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/rule/RuleEngine.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/rule/MinimumScoreRule.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/rule/MaxLatePaymentsRule.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/rule/AvailableLimitRule.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/rule/LimitCommitmentRule.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/rule/RecentSpendingTrendRule.kt, src/test/kotlin/io/github/brdoliveira/creditflow/domain/rule/RuleEngineTest.kt
  mensagem de commit: "T-002 credito-rotativo: Implementar domínio e motor extensível de regras"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-2.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-2' 'spec/credito-rotativo-faixa-2' "$WT" "$st" || return 1
  marcar_concluidas T-002
  return 0
}

# ── faixa-3: T-003 ──
executar_faixa_3() {
  local WT="$WT_BASE-faixa-3"
  preparar_worktree 'faixa-3' 'spec/credito-rotativo-faixa-3' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-3' --estado executando --tentativa "$(tentativa 'faixa-3')"
  : > "$LOG_DIR/faixa-3.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-3' 'T-003' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-003 — "Implementar cálculo do crédito"
  critérios/refs: AC-012 (Cliente elegível recebe valor calculado), AC-013 (Cálculo usa precisão monetária), AC-014 (Cliente reprovado não executa concessão)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/domain/calculation/CreditLimitCalculator.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/calculation/ConfigurableCreditLimitCalculator.kt, src/main/kotlin/io/github/brdoliveira/creditflow/domain/calculation/CreditCalculationPolicy.kt, src/test/kotlin/io/github/brdoliveira/creditflow/domain/calculation/CreditLimitCalculatorTest.kt
  mensagem de commit: "T-003 credito-rotativo: Implementar cálculo do crédito"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-3.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-3' 'spec/credito-rotativo-faixa-3' "$WT" "$st" || return 1
  marcar_concluidas T-003
  return 0
}

# ── faixa-4: T-004 ──
executar_faixa_4() {
  local WT="$WT_BASE-faixa-4"
  preparar_worktree 'faixa-4' 'spec/credito-rotativo-faixa-4' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-4' --estado executando --tentativa "$(tentativa 'faixa-4')"
  : > "$LOG_DIR/faixa-4.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-4' 'T-004' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-004 — "Implementar caso de uso de avaliação"
  critérios/refs: AC-001 (Avaliação válida é criada), AC-003 (Reprovação de crédito não é erro técnico), AC-015 (Resposta contém rastreabilidade), AC-016 (Avaliação persiste a fotografia da decisão)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/application/evaluation/EvaluateRevolvingCreditUseCase.kt, src/main/kotlin/io/github/brdoliveira/creditflow/application/evaluation/EvaluateCreditCommand.kt, src/main/kotlin/io/github/brdoliveira/creditflow/application/evaluation/CreditEvaluationResult.kt, src/test/kotlin/io/github/brdoliveira/creditflow/application/evaluation/EvaluateRevolvingCreditUseCaseTest.kt
  mensagem de commit: "T-004 credito-rotativo: Implementar caso de uso de avaliação"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-4.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-4' 'spec/credito-rotativo-faixa-4' "$WT" "$st" || return 1
  marcar_concluidas T-004
  return 0
}

# ── faixa-5: T-005 ──
executar_faixa_5() {
  local WT="$WT_BASE-faixa-5"
  preparar_worktree 'faixa-5' 'spec/credito-rotativo-faixa-5' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-5' --estado executando --tentativa "$(tentativa 'faixa-5')"
  : > "$LOG_DIR/faixa-5.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-5' 'T-005' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-005 — "Implementar API REST e contrato de erros"
  critérios/refs: AC-001 (Avaliação válida é criada), AC-002 (Dados inválidos são explicados), AC-003 (Reprovação de crédito não é erro técnico), AC-022 (Listagem é paginada e filtrável), AC-023 (Avaliação pode ser consultada por identificador), AC-024 (Avaliação inexistente retorna resposta padronizada), AC-028 (Filtros inválidos são rejeitados), AC-040 (Erro técnico tem resposta correlacionável)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationController.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationRequest.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationResponse.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/ApiError.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/GlobalExceptionHandler.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationControllerTest.kt
  mensagem de commit: "T-005 credito-rotativo: Implementar API REST e contrato de erros"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-5.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-5' 'spec/credito-rotativo-faixa-5' "$WT" "$st" || return 1
  marcar_concluidas T-005
  return 0
}

# ── faixa-6: T-006 ──
executar_faixa_6() {
  local WT="$WT_BASE-faixa-6"
  preparar_worktree 'faixa-6' 'spec/credito-rotativo-faixa-6' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-6' --estado executando --tentativa "$(tentativa 'faixa-6')"
  : > "$LOG_DIR/faixa-6.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-6' 'T-006' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-006 — "Implementar persistência PostgreSQL e auditoria"
  critérios/refs: AC-015 (Resposta contém rastreabilidade), AC-016 (Avaliação persiste a fotografia da decisão), AC-017 (CPF completo não vaza), AC-022 (Listagem é paginada e filtrável), AC-023 (Avaliação pode ser consultada por identificador), AC-024 (Avaliação inexistente retorna resposta padronizada)
  arquivos permitidos (e seus testes): src/main/resources/db/migration/V1__credit_evaluation.sql, src/main/kotlin/io/github/brdoliveira/creditflow/application/port/CreditEvaluationRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/persistence/PostgresCreditEvaluationRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/persistence/CreditEvaluationEntity.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/privacy/CpfProtector.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/persistence/PostgresCreditEvaluationRepositoryIT.kt
  mensagem de commit: "T-006 credito-rotativo: Implementar persistência PostgreSQL e auditoria"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-6.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-6' 'spec/credito-rotativo-faixa-6' "$WT" "$st" || return 1
  marcar_concluidas T-006
  return 0
}

# ── faixa-7: T-007 ──
executar_faixa_7() {
  local WT="$WT_BASE-faixa-7"
  preparar_worktree 'faixa-7' 'spec/credito-rotativo-faixa-7' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-7' --estado executando --tentativa "$(tentativa 'faixa-7')"
  : > "$LOG_DIR/faixa-7.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-7' 'T-007' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-007 — "Implementar idempotência concorrente"
  critérios/refs: AC-018 (Chave de idempotência é obrigatória), AC-019 (Repetição idêntica devolve o resultado original), AC-020 (Reutilização divergente é rejeitada), AC-021 (Concorrência não duplica avaliação)
  arquivos permitidos (e seus testes): src/main/resources/db/migration/V2__credit_idempotency.sql, src/main/kotlin/io/github/brdoliveira/creditflow/application/port/IdempotencyRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/idempotency/PostgresIdempotencyRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/idempotency/CanonicalRequestHasher.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/idempotency/IdempotencyIT.kt
  mensagem de commit: "T-007 credito-rotativo: Implementar idempotência concorrente"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-7.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-7' 'spec/credito-rotativo-faixa-7' "$WT" "$st" || return 1
  marcar_concluidas T-007
  return 0
}

# ── faixa-8: T-008 ──
executar_faixa_8() {
  local WT="$WT_BASE-faixa-8"
  preparar_worktree 'faixa-8' 'spec/credito-rotativo-faixa-8' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-8' --estado executando --tentativa "$(tentativa 'faixa-8')"
  : > "$LOG_DIR/faixa-8.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-8' 'T-008' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-008 — "Implementar Outbox, publicação e consumo idempotente"
  critérios/refs: AC-033 (Avaliação e Outbox são atômicas), AC-034 (Evento possui contrato versionado e sem CPF completo), AC-035 (Publicação temporariamente falha e tenta novamente), AC-036 (Consumidor ignora evento duplicado)
  arquivos permitidos (e seus testes): src/main/resources/db/migration/V3__credit_outbox.sql, src/main/kotlin/io/github/brdoliveira/creditflow/application/event/CreditEvaluationCompleted.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox/OutboxPublisher.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationEventProducer.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/IdempotentCreditEvaluationConsumer.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationMessagingIT.kt
  mensagem de commit: "T-008 credito-rotativo: Implementar Outbox, publicação e consumo idempotente"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-8.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-8' 'spec/credito-rotativo-faixa-8' "$WT" "$st" || return 1
  marcar_concluidas T-008
  return 0
}

# ── faixa-9: T-009 ──
executar_faixa_9() {
  local WT="$WT_BASE-faixa-9"
  preparar_worktree 'faixa-9' 'spec/credito-rotativo-faixa-9' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-9' --estado executando --tentativa "$(tentativa 'faixa-9')"
  : > "$LOG_DIR/faixa-9.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-9' 'T-009' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-009 — "Implementar autenticação e autorização"
  critérios/refs: AC-029 (Token ausente ou inválido é rejeitado), AC-030 (Permissão insuficiente é rejeitada), AC-031 (Escopos separam as operações), AC-032 (Transporte produtivo exige HTTPS)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/SecurityConfiguration.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/ScopeAuthoritiesConverter.kt, src/main/resources/application-security.yml, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/ApiSecurityTest.kt, docker/keycloak/realm-export.json
  mensagem de commit: "T-009 credito-rotativo: Implementar autenticação e autorização"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-9.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-9' 'spec/credito-rotativo-faixa-9' "$WT" "$st" || return 1
  marcar_concluidas T-009
  return 0
}

# ── faixa-10: T-010 ──
executar_faixa_10() {
  local WT="$WT_BASE-faixa-10"
  preparar_worktree 'faixa-10' 'spec/credito-rotativo-faixa-10' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-10' --estado executando --tentativa "$(tentativa 'faixa-10')"
  : > "$LOG_DIR/faixa-10.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-10' 'T-010' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-010 — "Implementar relatório PDF"
  critérios/refs: AC-025 (PDF válido é gerado no backend), AC-026 (PDF contém os dados exigidos), AC-027 (Relatório vazio continua válido), AC-028 (Filtros inválidos são rejeitados)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/application/report/CreditEvaluationReportGenerator.kt, src/main/kotlin/io/github/brdoliveira/creditflow/application/report/CreditEvaluationReportFilter.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/report/PdfCreditEvaluationReportGenerator.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationReportController.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/report/PdfCreditEvaluationReportTest.kt
  mensagem de commit: "T-010 credito-rotativo: Implementar relatório PDF"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-10.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-10' 'spec/credito-rotativo-faixa-10' "$WT" "$st" || return 1
  marcar_concluidas T-010
  return 0
}

# ── faixa-11: T-011 ──
executar_faixa_11() {
  local WT="$WT_BASE-faixa-11"
  preparar_worktree 'faixa-11' 'spec/credito-rotativo-faixa-11' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-11' --estado executando --tentativa "$(tentativa 'faixa-11')"
  : > "$LOG_DIR/faixa-11.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-11' 'T-011' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-011 — "Implementar frontend demonstrativo"
  critérios/refs: AC-041 (Tela de avaliação apresenta a decisão explicável), AC-042 (Tela de relatório reutiliza os filtros), AC-043 (Erros são apresentados sem detalhes internos)
  arquivos permitidos (e seus testes): src/main/resources/static/index.html, src/main/resources/static/report.html, src/main/resources/static/css/app.css, src/main/resources/static/ts/api.ts, src/main/resources/static/ts/evaluation.ts, src/main/resources/static/ts/report.ts, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/FrontendSmokeTest.kt
  mensagem de commit: "T-011 credito-rotativo: Implementar frontend demonstrativo"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-11.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-11' 'spec/credito-rotativo-faixa-11' "$WT" "$st" || return 1
  marcar_concluidas T-011
  return 0
}

# ── faixa-12: T-012 ──
executar_faixa_12() {
  local WT="$WT_BASE-faixa-12"
  preparar_worktree 'faixa-12' 'spec/credito-rotativo-faixa-12' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-12' --estado executando --tentativa "$(tentativa 'faixa-12')"
  : > "$LOG_DIR/faixa-12.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-12' 'T-012' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-012 — "Implementar observabilidade e resiliência"
  critérios/refs: AC-037 (Correlação acompanha a requisição), AC-038 (Métricas essenciais são expostas), AC-039 (Health checks distinguem vida e prontidão), AC-040 (Erro técnico tem resposta correlacionável)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/CorrelationIdFilter.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/CreditMetrics.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/health/DependencyReadinessIndicator.kt, src/main/resources/logback-spring.xml, src/main/resources/application-observability.yml, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/ObservabilityTest.kt
  mensagem de commit: "T-012 credito-rotativo: Implementar observabilidade e resiliência"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-12.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-12' 'spec/credito-rotativo-faixa-12' "$WT" "$st" || return 1
  marcar_concluidas T-012
  return 0
}

# ── faixa-13: T-013 ──
executar_faixa_13() {
  local WT="$WT_BASE-faixa-13"
  preparar_worktree 'faixa-13' 'spec/credito-rotativo-faixa-13' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-13' --estado executando --tentativa "$(tentativa 'faixa-13')"
  : > "$LOG_DIR/faixa-13.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-13' 'T-013' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-013 — "Preparar execução local em containers"
  critérios/refs: AC-044 (Ambiente local sobe por Docker Compose)
  arquivos permitidos (e seus testes): Dockerfile, compose.yaml, .env.example, docker/postgres/init.sql, docker/kafka/README.md
  mensagem de commit: "T-013 credito-rotativo: Preparar execução local em containers"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-13.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-13' 'spec/credito-rotativo-faixa-13' "$WT" "$st" || return 1
  marcar_concluidas T-013
  return 0
}

# ── faixa-14: T-014 ──
executar_faixa_14() {
  local WT="$WT_BASE-faixa-14"
  preparar_worktree 'faixa-14' 'spec/credito-rotativo-faixa-14' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-14' --estado executando --tentativa "$(tentativa 'faixa-14')"
  : > "$LOG_DIR/faixa-14.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-14' 'T-014' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-014 — "Criar pipeline de CI e gates"
  critérios/refs: AC-045 (Pipeline bloqueia mudança inválida)
  arquivos permitidos (e seus testes): .github/workflows/ci.yml, config/detekt/detekt.yml, .spec/README.md
  mensagem de commit: "T-014 credito-rotativo: Criar pipeline de CI e gates"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-14.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-14' 'spec/credito-rotativo-faixa-14' "$WT" "$st" || return 1
  marcar_concluidas T-014
  return 0
}

# ── faixa-15: T-015 ──
executar_faixa_15() {
  local WT="$WT_BASE-faixa-15"
  preparar_worktree 'faixa-15' 'spec/credito-rotativo-faixa-15' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-15' --estado executando --tentativa "$(tentativa 'faixa-15')"
  : > "$LOG_DIR/faixa-15.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-15' 'T-015' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-015 — "Criar e documentar teste de carga"
  critérios/refs: AC-046 (Carga nominal atende aos objetivos)
  arquivos permitidos (e seus testes): performance/k6/credit-evaluation.js, performance/README.md, src/test/resources/performance/valid-credit-evaluation.json
  mensagem de commit: "T-015 credito-rotativo: Criar e documentar teste de carga"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-15.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-15' 'spec/credito-rotativo-faixa-15' "$WT" "$st" || return 1
  marcar_concluidas T-015
  return 0
}

# ── faixa-16: T-016 ──
executar_faixa_16() {
  local WT="$WT_BASE-faixa-16"
  preparar_worktree 'faixa-16' 'spec/credito-rotativo-faixa-16' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-16' --estado executando --tentativa "$(tentativa 'faixa-16')"
  : > "$LOG_DIR/faixa-16.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-16' 'T-016' 'Você executa UMA tarefa da feature "credito-rotativo" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/credito-rotativo/spec.md, .spec/features/credito-rotativo/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-016 — "Consolidar README, ADRs e arquitetura cloud"
  critérios/refs: AC-047 (Documentação permite reprodução e defesa técnica)
  arquivos permitidos (e seus testes): README.md, docs/architecture.md, docs/adrs/001-modular-monolith.md, docs/adrs/002-postgresql.md, docs/adrs/003-outbox-messaging.md, docs/adrs/004-pdf-library.md, docs/adrs/005-ecs-vs-eks.md, docs/adrs/006-aurora-vs-dynamodb.md, docs/ai-usage.md
  mensagem de commit: "T-016 credito-rotativo: Consolidar README, ADRs e arquitetura cloud"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `node --test` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-16.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-16' 'spec/credito-rotativo-faixa-16' "$WT" "$st" || return 1
  marcar_concluidas T-016
  return 0
}

# ── gate: quem decide é a máquina ────────────────────────────────────
rodar_gate() {
  echo
  info "gate: verify + audit --ci"
  evento --tipo gate --etapa inicio
  node "$ENGINE" verify "$FEATURE"
  local v=$?
  evento --tipo gate --etapa verify --exit "$v"
  node "$ENGINE" audit --ci
  AUDIT=$?
  evento --tipo gate --etapa audit --exit "$AUDIT"
  # fecha a contabilidade: status das tarefas + prova do verify no git
  if [ -n "$(git status --porcelain -- '.spec')" ]; then
    git add -A -- '.spec'
    git commit -q -m "$FEATURE: status das tarefas + prova do verify (plano)"
    info "status das tarefas e prova do verify commitados"
  fi
  return "$AUDIT"
}

encerrar() { # $1=escopo
  echo
  if [ -n "$FALHAS" ]; then vermelho "faixas/tarefas com falha:$FALHAS"; fi
  # sem gate não existe veredito: NUNCA anunciar alinhamento sem o audit
  if [ "$COM_GATE" -eq 0 ]; then
    evento --tipo fim --exit 1 --escopo "$1"
    if [ -z "$FALHAS" ]; then
      amarelo "○ trabalho de '$1' terminou SEM o gate (--sem-gate) — isto NÃO é prova de nada"
      amarelo "  para o veredito: bash .spec/features/credito-rotativo/executar-tarefas.sh --gate"
      exit 0
    fi
    vermelho "e ainda há falhas — conserte e rode o gate"
    exit 1
  fi
  rodar_gate
  local audit=$?
  if [ "$audit" -eq 0 ] && [ -z "$FALHAS" ]; then
    evento --tipo fim --exit 0 --escopo "$1"
    verde "✔ plano concluído — especificação e código alinhados (audit exit 0) na branch $BASE_BRANCH"
    info "próximo passo: revise e leve para a main quando quiser (git merge $BASE_BRANCH)"
    exit 0
  fi
  evento --tipo fim --exit 1 --escopo "$1"
  vermelho "plano terminou com pendências — leia a saída do audit acima e os logs em $LOG_DIR"
  amarelo "dica: reexecute só o que falhou (--faixa <id> / --seq <T-xxx>)"
  exit 1
}

executar_tudo() {
  evento --tipo inicio --escopo tudo
  iniciar_resumos
  info "logs em: $LOG_DIR"
  info "resumo geral de andamento: a cada 1 min aqui no terminal (e via: onp-spec resumo)"
  # onda 1: faixa-1 ∥ faixa-2 ∥ faixa-3
  info "onda 1: faixa-1 ∥ faixa-2 ∥ faixa-3 — janelas limpas em paralelo"
  executar_faixa_1 & PID_FAIXA_1=$!
  executar_faixa_2 & PID_FAIXA_2=$!
  executar_faixa_3 & PID_FAIXA_3=$!
  wait "$PID_FAIXA_1" || true
  wait "$PID_FAIXA_2" || true
  wait "$PID_FAIXA_3" || true
  # onda 2: faixa-4 ∥ faixa-5 ∥ faixa-6
  info "onda 2: faixa-4 ∥ faixa-5 ∥ faixa-6 — janelas limpas em paralelo"
  executar_faixa_4 & PID_FAIXA_4=$!
  executar_faixa_5 & PID_FAIXA_5=$!
  executar_faixa_6 & PID_FAIXA_6=$!
  wait "$PID_FAIXA_4" || true
  wait "$PID_FAIXA_5" || true
  wait "$PID_FAIXA_6" || true
  # onda 3: faixa-7 ∥ faixa-8 ∥ faixa-9
  info "onda 3: faixa-7 ∥ faixa-8 ∥ faixa-9 — janelas limpas em paralelo"
  executar_faixa_7 & PID_FAIXA_7=$!
  executar_faixa_8 & PID_FAIXA_8=$!
  executar_faixa_9 & PID_FAIXA_9=$!
  wait "$PID_FAIXA_7" || true
  wait "$PID_FAIXA_8" || true
  wait "$PID_FAIXA_9" || true
  # onda 4: faixa-10 ∥ faixa-11 ∥ faixa-12
  info "onda 4: faixa-10 ∥ faixa-11 ∥ faixa-12 — janelas limpas em paralelo"
  executar_faixa_10 & PID_FAIXA_10=$!
  executar_faixa_11 & PID_FAIXA_11=$!
  executar_faixa_12 & PID_FAIXA_12=$!
  wait "$PID_FAIXA_10" || true
  wait "$PID_FAIXA_11" || true
  wait "$PID_FAIXA_12" || true
  # onda 5: faixa-13 ∥ faixa-14 ∥ faixa-15
  info "onda 5: faixa-13 ∥ faixa-14 ∥ faixa-15 — janelas limpas em paralelo"
  executar_faixa_13 & PID_FAIXA_13=$!
  executar_faixa_14 & PID_FAIXA_14=$!
  executar_faixa_15 & PID_FAIXA_15=$!
  wait "$PID_FAIXA_13" || true
  wait "$PID_FAIXA_14" || true
  wait "$PID_FAIXA_15" || true
  # onda 6: faixa-16
  info "onda 6: faixa-16 — janelas limpas em paralelo"
  executar_faixa_16 & PID_FAIXA_16=$!
  wait "$PID_FAIXA_16" || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  faixa-1  onda 1  T-001"
  echo "  faixa-2  onda 1  T-002"
  echo "  faixa-3  onda 1  T-003"
  echo "  faixa-4  onda 2  T-004"
  echo "  faixa-5  onda 2  T-005"
  echo "  faixa-6  onda 2  T-006"
  echo "  faixa-7  onda 3  T-007"
  echo "  faixa-8  onda 3  T-008"
  echo "  faixa-9  onda 3  T-009"
  echo "  faixa-10  onda 4  T-010"
  echo "  faixa-11  onda 4  T-011"
  echo "  faixa-12  onda 4  T-012"
  echo "  faixa-13  onda 5  T-013"
  echo "  faixa-14  onda 5  T-014"
  echo "  faixa-15  onda 5  T-015"
  echo "  faixa-16  onda 6  T-016"
  echo
  echo "reexecutar uma faixa:    --faixa <id>"
  echo "reexecutar sequencial:   --seq <T-xxx>"
  echo "só o gate:               --gate"
}

MODO="tudo"
ALVO=""
while [ $# -gt 0 ]; do
  case "$1" in
    --listar) MODO="listar" ;;
    --gate) MODO="gate" ;;
    --sem-gate) COM_GATE=0 ;;
    --faixa) MODO="faixa"; ALVO="${2:-}"; shift ;;
    --seq) MODO="seq"; ALVO="${2:-}"; shift ;;
    -h|--help) sed -n "2,14p" "$0"; exit 0 ;;
    *) vermelho "argumento desconhecido: $1"; sed -n "2,14p" "$0"; exit 2 ;;
  esac
  shift
done

if [ "$MODO" = "listar" ]; then listar; exit 0; fi

preparar_ambiente

case "$MODO" in
  tudo) executar_tudo ;;
  gate) COM_GATE=1; iniciar_resumos; encerrar gate ;;
  faixa)
    case "$ALVO" in
      faixa-1) evento --tipo inicio --escopo "faixa:faixa-1"; iniciar_resumos; executar_faixa_1 || true; encerrar "faixa:faixa-1" ;;
      faixa-2) evento --tipo inicio --escopo "faixa:faixa-2"; iniciar_resumos; executar_faixa_2 || true; encerrar "faixa:faixa-2" ;;
      faixa-3) evento --tipo inicio --escopo "faixa:faixa-3"; iniciar_resumos; executar_faixa_3 || true; encerrar "faixa:faixa-3" ;;
      faixa-4) evento --tipo inicio --escopo "faixa:faixa-4"; iniciar_resumos; executar_faixa_4 || true; encerrar "faixa:faixa-4" ;;
      faixa-5) evento --tipo inicio --escopo "faixa:faixa-5"; iniciar_resumos; executar_faixa_5 || true; encerrar "faixa:faixa-5" ;;
      faixa-6) evento --tipo inicio --escopo "faixa:faixa-6"; iniciar_resumos; executar_faixa_6 || true; encerrar "faixa:faixa-6" ;;
      faixa-7) evento --tipo inicio --escopo "faixa:faixa-7"; iniciar_resumos; executar_faixa_7 || true; encerrar "faixa:faixa-7" ;;
      faixa-8) evento --tipo inicio --escopo "faixa:faixa-8"; iniciar_resumos; executar_faixa_8 || true; encerrar "faixa:faixa-8" ;;
      faixa-9) evento --tipo inicio --escopo "faixa:faixa-9"; iniciar_resumos; executar_faixa_9 || true; encerrar "faixa:faixa-9" ;;
      faixa-10) evento --tipo inicio --escopo "faixa:faixa-10"; iniciar_resumos; executar_faixa_10 || true; encerrar "faixa:faixa-10" ;;
      faixa-11) evento --tipo inicio --escopo "faixa:faixa-11"; iniciar_resumos; executar_faixa_11 || true; encerrar "faixa:faixa-11" ;;
      faixa-12) evento --tipo inicio --escopo "faixa:faixa-12"; iniciar_resumos; executar_faixa_12 || true; encerrar "faixa:faixa-12" ;;
      faixa-13) evento --tipo inicio --escopo "faixa:faixa-13"; iniciar_resumos; executar_faixa_13 || true; encerrar "faixa:faixa-13" ;;
      faixa-14) evento --tipo inicio --escopo "faixa:faixa-14"; iniciar_resumos; executar_faixa_14 || true; encerrar "faixa:faixa-14" ;;
      faixa-15) evento --tipo inicio --escopo "faixa:faixa-15"; iniciar_resumos; executar_faixa_15 || true; encerrar "faixa:faixa-15" ;;
      faixa-16) evento --tipo inicio --escopo "faixa:faixa-16"; iniciar_resumos; executar_faixa_16 || true; encerrar "faixa:faixa-16" ;;
      *) falhar "faixa desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
  seq)
    case "$ALVO" in
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
