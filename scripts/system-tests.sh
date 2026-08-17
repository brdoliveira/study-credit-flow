#!/usr/bin/env bash

set -Eeuo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PROJECT_NAME="${SYSTEM_TEST_PROJECT:-credit-flow-system-${RANDOM}}"
readonly BASE_PORT="${SYSTEM_TEST_BASE_PORT:-$((20000 + RANDOM % 10000))}"

fail() { printf 'Erro: %s\n' "$*" >&2; exit 1; }
log() { printf '\n==> %s\n' "$*"; }
read_env() { sed -n "s/^$1=//p" .env | tail -n 1; }

for dependency in docker node npm; do
  command -v "$dependency" >/dev/null 2>&1 || fail "dependencia ausente: $dependency"
done
docker info >/dev/null 2>&1 || fail "Docker nao esta em execucao"
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
  export E2E_COMPOSE_COMMAND="$(command -v docker-compose)"
else
  fail "Docker Compose nao foi encontrado"
fi

cd "$ROOT_DIR"
[[ -f .env ]] || fail "arquivo .env ausente; crie-o a partir de .env.example"
[[ "$(read_env CREDIT_DEMO_PASSWORD)" != replace-with-* ]] || fail ".env ainda contem placeholders"

export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
export APP_PORT="$BASE_PORT"
export POSTGRES_PORT="$((BASE_PORT + 1))"
export KEYCLOAK_PORT="$((BASE_PORT + 2))"
export KAFKA_PORT="$((BASE_PORT + 3))"
export E2E_APP_URL="http://localhost:$APP_PORT"
export E2E_KEYCLOAK_URL="http://localhost:$KEYCLOAK_PORT"
export E2E_COMPOSE_PROJECT="$PROJECT_NAME"
export CREDIT_DEMO_PASSWORD="$(read_env CREDIT_DEMO_PASSWORD)"
export APP_IMAGE="${PROJECT_NAME}-app:local"

cleanup() {
  local status=$?
  if ((status != 0)); then
    "${COMPOSE[@]}" ps || true
    "${COMPOSE[@]}" logs --tail=200 app postgres kafka keycloak || true
  fi
  "${COMPOSE[@]}" down --volumes --remove-orphans --rmi local >/dev/null 2>&1 || true
  docker image rm "$APP_IMAGE" >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT

log "Construindo e iniciando a pilha isolada $PROJECT_NAME"
./gradlew --no-daemon bootJar
"${COMPOSE[@]}" up --detach --build --wait --wait-timeout 240

log "Executando jornada HTTP e mensageria"
node --test --test-concurrency=1 --test-reporter=tap test/e2e/credit-flow.spec.mjs

log "Executando jornada real no Chromium e auditoria WCAG"
npm run test:browser

log "Interrompendo dependencias e validando recuperacao"
node --test --test-concurrency=1 --test-reporter=tap test/resilience/dependency-recovery.spec.mjs

log "Validando backup, rotacao de segredo e rollback"
./scripts/recovery-drill.sh

log "Gate de sistema concluido"
