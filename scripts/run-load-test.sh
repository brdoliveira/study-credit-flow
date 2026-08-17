#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

KEEP_STACK=false
EVIDENCE_FILE="${LOAD_TEST_EVIDENCE_FILE:-.context/load-test-summary.json}"
PDF_FILE="${LOAD_TEST_PDF_FILE:-.context/load-test-report.pdf}"

usage() {
  cat <<'EOF'
Uso: ./scripts/run-load-test.sh [opcoes]

Sobe uma pilha Docker isolada, obtem um token no Keycloak, executa o cenario k6,
grava a evidencia JSON e remove os containers e volumes ao terminar.

Opcoes:
  --evidence ARQUIVO  Caminho do relatorio JSON (padrao: .context/load-test-summary.json)
  --pdf ARQUIVO       Caminho do relatorio PDF (padrao: .context/load-test-report.pdf)
  --keep-stack        Mantem os containers e volumes depois do teste
  -h, --help          Exibe esta ajuda

Variaveis opcionais:
  LOAD_TEST_PROJECT, LOAD_TEST_USERNAME, LOAD_TEST_PASSWORD,
  APP_PORT, POSTGRES_PORT, KEYCLOAK_PORT, KAFKA_PORT,
  LOAD_TEST_ENVIRONMENT, LOAD_TEST_RESOURCES e LOAD_TEST_PDF_FILE.
EOF
}

fail() {
  printf 'Erro: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '\n==> %s\n' "$*"
}

while (($# > 0)); do
  case "$1" in
    --evidence)
      (($# >= 2)) || fail "informe um arquivo depois de --evidence"
      EVIDENCE_FILE="$2"
      shift 2
      ;;
    --keep-stack)
      KEEP_STACK=true
      shift
      ;;
    --pdf)
      (($# >= 2)) || fail "informe um arquivo depois de --pdf"
      PDF_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "opcao desconhecida: $1"
      ;;
  esac
done

for dependency in docker k6 node git curl; do
  command -v "$dependency" >/dev/null 2>&1 || fail "dependencia ausente: $dependency"
done

if command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
elif docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
else
  fail "Docker Compose nao foi encontrado"
fi

docker info >/dev/null 2>&1 || fail "o Docker nao esta em execucao"

cd "$PROJECT_ROOT"
[[ -f .env ]] || fail "arquivo .env ausente; crie-o a partir de .env.example"

read_dotenv_value() {
  local key="$1"
  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      sub(/\r$/, "")
      print
      exit
    }
  ' .env
}

LOAD_TEST_PASSWORD="${LOAD_TEST_PASSWORD:-${CREDIT_DEMO_PASSWORD:-}}"
if [[ -z "$LOAD_TEST_PASSWORD" ]]; then
  LOAD_TEST_PASSWORD="$(read_dotenv_value CREDIT_DEMO_PASSWORD)"
fi
[[ -n "$LOAD_TEST_PASSWORD" ]] || fail "defina LOAD_TEST_PASSWORD ou CREDIT_DEMO_PASSWORD no .env"

export COMPOSE_PROJECT_NAME="${LOAD_TEST_PROJECT:-credit-load}"
export APP_PORT="${APP_PORT:-18080}"
export POSTGRES_PORT="${POSTGRES_PORT:-15432}"
export KEYCLOAK_PORT="${KEYCLOAK_PORT:-18180}"
export KAFKA_PORT="${KAFKA_PORT:-19092}"
export CREDIT_DEMO_PASSWORD="$LOAD_TEST_PASSWORD"

cleanup() {
  local status=$?
  trap - EXIT

  unset AUTHORIZATION LOAD_TEST_PASSWORD CREDIT_DEMO_PASSWORD
  if [[ "$KEEP_STACK" == true ]]; then
    log "Pilha $COMPOSE_PROJECT_NAME mantida (--keep-stack)"
    printf 'API: http://localhost:%s\nKeycloak: http://localhost:%s\n' "$APP_PORT" "$KEYCLOAK_PORT"
  else
    log "Removendo a pilha isolada $COMPOSE_PROJECT_NAME"
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi

  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_service() {
  local service="$1"
  local timeout_seconds="${2:-240}"
  local deadline=$((SECONDS + timeout_seconds))
  local container_id status

  while ((SECONDS < deadline)); do
    container_id="$("${COMPOSE[@]}" ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      case "$status" in
        healthy)
          printf '  %s: healthy\n' "$service"
          return 0
          ;;
        exited|dead|unhealthy)
          "${COMPOSE[@]}" logs --no-color "$service" >&2 || true
          fail "servico $service terminou com status $status"
          ;;
      esac
    fi
    sleep 3
  done

  "${COMPOSE[@]}" logs --no-color "$service" >&2 || true
  fail "timeout aguardando o servico $service"
}

log "Limpando uma eventual pilha isolada anterior"
"${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true

log "Gerando o JAR executavel da aplicacao"
./gradlew bootJar --no-daemon

log "Construindo e iniciando a pilha $COMPOSE_PROJECT_NAME"
"${COMPOSE[@]}" up --detach --build

log "Aguardando os servicos"
for service in postgres keycloak kafka app; do
  wait_for_service "$service"
done

token_url="http://localhost:${KEYCLOAK_PORT}/realms/credit-rotativo/protocol/openid-connect/token"
username="${LOAD_TEST_USERNAME:-credit-writer}"
token=""

log "Obtendo credencial temporaria no Keycloak"
for attempt in {1..10}; do
  response="$(curl --fail --silent --show-error \
    --request POST "$token_url" \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=credit-local' \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$LOAD_TEST_PASSWORD" 2>/dev/null || true)"

  if [[ -n "$response" ]]; then
    token="$(printf '%s' "$response" | node -e '
      let input = "";
      process.stdin.on("data", chunk => input += chunk);
      process.stdin.on("end", () => {
        try { process.stdout.write(JSON.parse(input).access_token || ""); } catch (_) {}
      });
    ')"
  fi

  [[ -n "$token" ]] && break
  sleep 3
done
[[ -n "$token" ]] || fail "nao foi possivel obter o token do usuario $username"

mkdir -p "$(dirname "$EVIDENCE_FILE")"
mkdir -p "$(dirname "$PDF_FILE")"
if [[ "$EVIDENCE_FILE" = /* ]]; then
  evidence_absolute_path="$EVIDENCE_FILE"
else
  evidence_absolute_path="$PROJECT_ROOT/$EVIDENCE_FILE"
fi
if [[ "$PDF_FILE" = /* ]]; then
  pdf_absolute_path="$PDF_FILE"
else
  pdf_absolute_path="$PROJECT_ROOT/$PDF_FILE"
fi

unset LOAD_TEST_PASSWORD CREDIT_DEMO_PASSWORD response
export BASE_URL="http://localhost:${APP_PORT}"
export AUTHORIZATION="Bearer $token"
export LOAD_TEST_COMMIT="$(git rev-parse HEAD)"
export LOAD_TEST_EXECUTED_AT_UTC="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
export LOAD_TEST_ENVIRONMENT="${LOAD_TEST_ENVIRONMENT:-local-isolated}"
export LOAD_TEST_RESOURCES="${LOAD_TEST_RESOURCES:-Docker Compose isolado em $(uname -s) $(uname -m)}"
export LOAD_TEST_EVIDENCE_FILE="$EVIDENCE_FILE"

log "Executando o cenario k6 (aproximadamente 6 minutos)"
set +e
k6 run performance/k6/credit-evaluation.js
k6_status=$?
set -e

unset AUTHORIZATION token
log "Gerando o relatorio PDF com grafico"
set +e
./gradlew generateLoadTestPdfReport \
  -PloadTestEvidence="$EVIDENCE_FILE" \
  -PloadTestPdf="$PDF_FILE" \
  --no-daemon
pdf_status=$?
set -e

if ((k6_status != 0)); then
  printf '\nTeste reprovado ou interrompido (codigo %d).\nJSON: %s\nPDF: %s\n' \
    "$k6_status" "$evidence_absolute_path" "$pdf_absolute_path" >&2
  exit "$k6_status"
fi
if ((pdf_status != 0)); then
  fail "o teste passou, mas nao foi possivel gerar o PDF"
fi

printf '\nTeste aprovado.\nJSON: %s\nPDF: %s\n' "$evidence_absolute_path" "$pdf_absolute_path"
