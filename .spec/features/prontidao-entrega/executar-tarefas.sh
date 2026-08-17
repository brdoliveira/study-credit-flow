#!/usr/bin/env bash
# executar-tarefas.sh — gerado por `onp-spec plano prontidao-entrega` em 2026-08-16 20:50
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
# resumo do que está rolando, a qualquer momento: onp-spec resumo prontidao-entrega
set -u
set -o pipefail

RUN_ID='study-credit-flow-prontidao-entrega-mswa3sz3'
FEATURE='prontidao-entrega'
BASE_BRANCH='spec/prontidao-entrega'
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
  git ls-files --error-unmatch -- '.spec/features/prontidao-entrega/spec.md' >/dev/null 2>&1 || falhar "spec.md não está commitada — os worktrees das faixas precisam dela no git"
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
  LOG_DIR="$(dirname "$TOPLEVEL")/onp-worktrees/study-credit-flow-prontidao-entrega-logs"
  WT_BASE="$(dirname "$TOPLEVEL")/onp-worktrees/study-credit-flow-prontidao-entrega"
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
    amarelo "  reexecute só ela: bash .spec/features/prontidao-entrega/executar-tarefas.sh --faixa $1"
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

# ── faixa-1: T-017 T-022 T-025 T-027 ──
executar_faixa_1() {
  local WT="$WT_BASE-faixa-1"
  preparar_worktree 'faixa-1' 'spec/prontidao-entrega-faixa-1' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-1' --estado executando --tentativa "$(tentativa 'faixa-1')"
  : > "$LOG_DIR/faixa-1.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-1' 'T-017' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-017 — "Implementar login OIDC pelo BFF"
  critérios/refs: AC-048 (Acesso interativo inicia o login corporativo), AC-049 (Tokens não ficam expostos ao JavaScript), AC-050 (Permissões controlam telas e operações), AC-051 (Logout encerra a sessão local), AC-052 (O mesmo emissor funciona dentro e fora dos containers)
  arquivos permitidos (e seus testes): build.gradle.kts, compose.yaml, docker/keycloak/realm-export.json, src/main/resources/application-security.yml, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/SecurityConfiguration.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/OidcSessionAuthoritiesMapper.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/SessionController.kt, src/main/resources/static/index.html, src/main/resources/static/report.html, src/main/resources/static/ts/api.ts, src/main/resources/static/ts/session.ts, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/security/OidcBrowserSecurityIT.kt
  mensagem de commit: "T-017 prontidao-entrega: Implementar login OIDC pelo BFF"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium &&
    rodar_tarefa 'faixa-1' 'T-022' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-022 — "Criar prova ponta a ponta do Docker Compose"
  critérios/refs: AC-052 (O mesmo emissor funciona dentro e fora dos containers), AC-060 (Evento ponta a ponta preserva contrato e privacidade), AC-065 (Docker Compose fica saudável a partir de um ambiente limpo), AC-066 (Jornada visual funciona ponta a ponta), AC-067 (Execução local não depende de segredos versionados)
  arquivos permitidos (e seus testes): compose.yaml, .env.example, docker/keycloak/realm-export.json, scripts/e2e-compose.ps1, test/e2e/credit-flow.spec.mjs, test/e2e/helpers.mjs, docs/evidence/compose-e2e.md
  mensagem de commit: "T-022 prontidao-entrega: Criar prova ponta a ponta do Docker Compose"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium &&
    rodar_tarefa 'faixa-1' 'T-025' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-025 — "Publicar e testar o contrato OpenAPI"
  critérios/refs: AC-074 (OpenAPI descreve operações e contratos relevantes), AC-075 (Contrato OpenAPI acompanha o comportamento)
  arquivos permitidos (e seus testes): build.gradle.kts, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/OpenApiConfiguration.kt, src/main/resources/openapi/credit-evaluations.yaml, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/OpenApiContractIT.kt, README.md
  mensagem de commit: "T-025 prontidao-entrega: Publicar e testar o contrato OpenAPI"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium &&
    rodar_tarefa 'faixa-1' 'T-027' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-027 — "Consolidar roteiro e índice de evidências"
  critérios/refs: AC-065 (Docker Compose fica saudável a partir de um ambiente limpo), AC-066 (Jornada visual funciona ponta a ponta), AC-067 (Execução local não depende de segredos versionados), AC-078 (README conduz a demonstração completa), AC-079 (Evidências identificam a revisão validada)
  arquivos permitidos (e seus testes): README.md, docs/evidence/README.md, docs/architecture.md, docs/ai-usage.md, scripts/demo.ps1, test/delivery-documentation.test.mjs
  mensagem de commit: "T-027 prontidao-entrega: Consolidar roteiro e índice de evidências"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-1.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-1' 'spec/prontidao-entrega-faixa-1' "$WT" "$st" || return 1
  marcar_concluidas T-017 T-022 T-025 T-027
  return 0
}

# ── faixa-2: T-018 ──
executar_faixa_2() {
  local WT="$WT_BASE-faixa-2"
  preparar_worktree 'faixa-2' 'spec/prontidao-entrega-faixa-2' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-2' --estado executando --tentativa "$(tentativa 'faixa-2')"
  : > "$LOG_DIR/faixa-2.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-2' 'T-018' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-018 — "Corrigir a semântica HTTP da idempotência"
  critérios/refs: AC-053 (Primeira execução retorna criação), AC-054 (Replay retorna sucesso sem nova criação), AC-055 (Concorrência e conflito são provados pela API real)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/application/port/IdempotencyRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/idempotency/PostgresIdempotencyRepository.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/DefaultCreditEvaluationApiService.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/CreditEvaluationController.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/IdempotentCreditEvaluationHttpIT.kt
  mensagem de commit: "T-018 prontidao-entrega: Corrigir a semântica HTTP da idempotência"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-2.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-2' 'spec/prontidao-entrega-faixa-2' "$WT" "$st" || return 1
  marcar_concluidas T-018
  return 0
}

# ── faixa-3: T-019 ──
executar_faixa_3() {
  local WT="$WT_BASE-faixa-3"
  preparar_worktree 'faixa-3' 'spec/prontidao-entrega-faixa-3' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-3' --estado executando --tentativa "$(tentativa 'faixa-3')"
  : > "$LOG_DIR/faixa-3.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-3' 'T-019' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-019 — "Conectar Outbox PostgreSQL ao Kafka"
  critérios/refs: AC-056 (Avaliação e Outbox são confirmadas atomicamente no PostgreSQL), AC-057 (Publisher envia o evento ao broker e confirma a Outbox), AC-058 (Falha transitória persiste tentativa e executa retry), AC-060 (Evento ponta a ponta preserva contrato e privacidade)
  arquivos permitidos (e seus testes): src/main/resources/db/migration/V4__outbox_runtime.sql, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox/PostgresOutboxStore.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox/OutboxPublisher.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/outbox/OutboxSchedulingConfiguration.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/KafkaBrokerPublisher.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationEventProducer.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/OutboxKafkaIT.kt
  mensagem de commit: "T-019 prontidao-entrega: Conectar Outbox PostgreSQL ao Kafka"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-3.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-3' 'spec/prontidao-entrega-faixa-3' "$WT" "$st" || return 1
  marcar_concluidas T-019
  return 0
}

# ── faixa-4: T-020 ──
executar_faixa_4() {
  local WT="$WT_BASE-faixa-4"
  preparar_worktree 'faixa-4' 'spec/prontidao-entrega-faixa-4' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-4' --estado executando --tentativa "$(tentativa 'faixa-4')"
  : > "$LOG_DIR/faixa-4.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-4' 'T-020' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-020 — "Implementar consumidor Kafka idempotente"
  critérios/refs: AC-059 (Consumidor Kafka aplica o efeito uma única vez), AC-060 (Evento ponta a ponta preserva contrato e privacidade)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/CreditEvaluationKafkaListener.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/PostgresProcessedEventStore.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/IdempotentCreditEvaluationConsumer.kt, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/messaging/IdempotentKafkaConsumerIT.kt
  mensagem de commit: "T-020 prontidao-entrega: Implementar consumidor Kafka idempotente"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-4.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-4' 'spec/prontidao-entrega-faixa-4' "$WT" "$st" || return 1
  marcar_concluidas T-020
  return 0
}

# ── faixa-5: T-021 ──
executar_faixa_5() {
  local WT="$WT_BASE-faixa-5"
  preparar_worktree 'faixa-5' 'spec/prontidao-entrega-faixa-5' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-5' --estado executando --tentativa "$(tentativa 'faixa-5')"
  : > "$LOG_DIR/faixa-5.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-5' 'T-021' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-021 — "Integrar métricas e health checks ao runtime"
  critérios/refs: AC-061 (Avaliações alimentam métricas de negócio e duração), AC-062 (Erros técnicos alimentam métricas operacionais), AC-063 (Readiness verifica dependências realmente obrigatórias), AC-064 (Endpoints operacionais têm política de acesso explícita)
  arquivos permitidos (e seus testes): src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/CreditMetrics.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/ObservedCreditEvaluationService.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/web/GlobalExceptionHandler.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/health/DependencyReadinessIndicator.kt, src/main/kotlin/io/github/brdoliveira/creditflow/infrastructure/config/ApplicationConfiguration.kt, src/main/resources/application-observability.yml, src/test/kotlin/io/github/brdoliveira/creditflow/infrastructure/observability/RuntimeObservabilityIT.kt
  mensagem de commit: "T-021 prontidao-entrega: Integrar métricas e health checks ao runtime"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-5.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-5' 'spec/prontidao-entrega-faixa-5' "$WT" "$st" || return 1
  marcar_concluidas T-021
  return 0
}

# ── faixa-6: T-023 ──
executar_faixa_6() {
  local WT="$WT_BASE-faixa-6"
  preparar_worktree 'faixa-6' 'spec/prontidao-entrega-faixa-6' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-6' --estado executando --tentativa "$(tentativa 'faixa-6')"
  : > "$LOG_DIR/faixa-6.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-6' 'T-023' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-023 — "Executar e registrar o ensaio de carga"
  critérios/refs: AC-068 (Ensaio nominal atinge os objetivos), AC-069 (Evidência de carga é rastreável), AC-070 (Reprovação de negócio não é contada como erro técnico)
  arquivos permitidos (e seus testes): performance/k6/credit-evaluation.js, performance/k6/summary.js, performance/README.md, scripts/run-load-test.ps1, docs/evidence/load-test-summary.json, docs/evidence/load-test.md, src/test/resources/performance/credit-evaluation.test.js
  mensagem de commit: "T-023 prontidao-entrega: Executar e registrar o ensaio de carga"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-6.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-6' 'spec/prontidao-entrega-faixa-6' "$WT" "$st" || return 1
  marcar_concluidas T-023
  return 0
}

# ── faixa-7: T-024 T-026 ──
executar_faixa_7() {
  local WT="$WT_BASE-faixa-7"
  preparar_worktree 'faixa-7' 'spec/prontidao-entrega-faixa-7' "$WT" || return 1
  evento --tipo faixa --faixa 'faixa-7' --estado executando --tentativa "$(tentativa 'faixa-7')"
  : > "$LOG_DIR/faixa-7.log"
  (
    cd "$WT" || exit 9
    rodar_tarefa 'faixa-7' 'T-024' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-024 — "Tornar CI e supply chain reproduzíveis"
  critérios/refs: AC-071 (CI verifica e audita a spec com ferramenta versionada), AC-072 (Integração real bloqueia a construção da imagem), AC-073 (Supply chain produz gates e inventário)
  arquivos permitidos (e seus testes): .github/workflows/ci.yml, tools/onp-spec/onp-spec.mjs, tools/onp-spec/lib, package.json, package-lock.json, scripts/junit-to-tap.mjs, config/security/trivy.yaml, config/security/gitleaks.toml, test/ci-pipeline.test.mjs
  mensagem de commit: "T-024 prontidao-entrega: Tornar CI e supply chain reproduzíveis"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium &&
    rodar_tarefa 'faixa-7' 'T-026' 'Você executa UMA tarefa da feature "prontidao-entrega" (fluxo onp-spec, spec-anchored).
Leia primeiro: .spec/features/prontidao-entrega/spec.md, .spec/features/prontidao-entrega/tasks.md e .spec/constituicao.md.

Sua tarefa (somente ela):
T-026 — "Criar infraestrutura AWS de referência"
  critérios/refs: AC-076 (Infraestrutura como código representa a arquitetura proposta), AC-077 (Configuração cloud aplica defaults seguros)
  arquivos permitidos (e seus testes): infrastructure/terraform/README.md, infrastructure/terraform/versions.tf, infrastructure/terraform/variables.tf, infrastructure/terraform/main.tf, infrastructure/terraform/outputs.tf, infrastructure/terraform/modules/network/main.tf, infrastructure/terraform/modules/service/main.tf, infrastructure/terraform/modules/database/main.tf, infrastructure/terraform/modules/messaging/main.tf, infrastructure/terraform/modules/observability/main.tf, test/terraform-contract.test.mjs, .github/workflows/ci.yml
  mensagem de commit: "T-026 prontidao-entrega: Criar infraestrutura AWS de referência"

Regras inegociáveis:
- Todo critério de aceite referenciado vira teste com @spec:AC-xxx no título.
- NUNCA enfraqueça, pule (skip/todo) ou apague um teste para passar — teste pulado não é prova e o audit acusa.
- Rode os testes localmente com `gradlew.bat test --no-daemon && node --test --test-reporter=tap "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts" && node scripts/junit-to-tap.mjs` até passarem.
- NÃO edite tasks.md, NÃO rode onp-spec verify/audit e NÃO toque em outras tarefas — o orquestrador cuida disso.
- Ao final de CADA tarefa: `git add` só no que você tocou e um commit próprio.' 'gpt-5.6-terra' medium
  ) >> "$LOG_DIR/faixa-7.log" 2>&1
  local st=$?
  mesclar_faixa 'faixa-7' 'spec/prontidao-entrega-faixa-7' "$WT" "$st" || return 1
  marcar_concluidas T-024 T-026
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
      amarelo "  para o veredito: bash .spec/features/prontidao-entrega/executar-tarefas.sh --gate"
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
  # onda 3: faixa-7
  info "onda 3: faixa-7 — janelas limpas em paralelo"
  executar_faixa_7 & PID_FAIXA_7=$!
  wait "$PID_FAIXA_7" || true
  encerrar tudo
}

listar() {
  echo "execução: $RUN_ID (feature $FEATURE, branch $BASE_BRANCH)"
  echo "  faixa-1  onda 1  T-017, T-022, T-025, T-027"
  echo "  faixa-2  onda 1  T-018"
  echo "  faixa-3  onda 1  T-019"
  echo "  faixa-4  onda 2  T-020"
  echo "  faixa-5  onda 2  T-021"
  echo "  faixa-6  onda 2  T-023"
  echo "  faixa-7  onda 3  T-024, T-026"
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
      *) falhar "faixa desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
  seq)
    case "$ALVO" in
      *) falhar "tarefa sequencial desconhecida: '$ALVO' — veja as disponíveis com --listar" ;;
    esac ;;
esac
