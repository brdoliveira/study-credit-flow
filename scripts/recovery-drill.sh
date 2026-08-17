#!/usr/bin/env bash

set -Eeuo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PROJECT_NAME="${E2E_COMPOSE_PROJECT:-${COMPOSE_PROJECT_NAME:-}}"
readonly RESTORE_DATABASE="credit_restore_${RANDOM}"
readonly BACKUP_DIRECTORY="$ROOT_DIR/.context/recovery"
readonly BACKUP_FILE="$BACKUP_DIRECTORY/postgres.dump"

fail() { printf 'Erro: %s\n' "$*" >&2; exit 1; }
log() { printf '\n--> %s\n' "$*"; }
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  fail "Docker Compose nao foi encontrado"
fi
compose() { "${COMPOSE[@]}" --project-name "$PROJECT_NAME" "$@"; }

[[ "$PROJECT_NAME" == credit-flow-system-* ]] || fail "o drill so pode rodar em um projeto descartavel credit-flow-system-*"
command -v openssl >/dev/null 2>&1 || fail "dependencia ausente: openssl"
mkdir -p "$BACKUP_DIRECTORY"
chmod 700 "$BACKUP_DIRECTORY"

drop_restore_database() {
  compose exec -T postgres sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists '$RESTORE_DATABASE'" >/dev/null 2>&1 || true
}
trap drop_restore_database EXIT

log "Criando backup PostgreSQL e restaurando em banco paralelo"
compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-acl' > "$BACKUP_FILE"
chmod 600 "$BACKUP_FILE"
[[ -s "$BACKUP_FILE" ]] || fail "backup vazio"
compose exec -T postgres sh -c "createdb -U \"\$POSTGRES_USER\" '$RESTORE_DATABASE'"
compose exec -T postgres sh -c "pg_restore -U \"\$POSTGRES_USER\" -d '$RESTORE_DATABASE' --no-owner --no-acl" < "$BACKUP_FILE"
source_signature="$(compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "select count(*) || chr(58) || coalesce(md5(string_agg(evaluation_id::text, chr(44) order by evaluation_id)), md5(chr(32))) from credit_evaluation"')"
restore_signature="$(compose exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d '$RESTORE_DATABASE' -tA -c \"select count(*) || chr(58) || coalesce(md5(string_agg(evaluation_id::text, chr(44) order by evaluation_id)), md5(chr(32))) from credit_evaluation\"")"
[[ "$source_signature" == "$restore_signature" ]] || fail "assinatura restaurada diverge da origem"
drop_restore_database

log "Simulando falha de deploy e rollback para a imagem conhecida"
current_image="$(docker inspect "$(compose ps -q app)" --format '{{.Config.Image}}')"
APP_IMAGE=busybox:1.36.1 compose up --detach --no-deps app >/dev/null
sleep 2
[[ "$(compose ps -a app --format json)" != *'"Health":"healthy"'* ]] || fail "a versao deliberadamente invalida ficou saudavel"
APP_IMAGE="$current_image" compose up --detach --no-deps app >/dev/null
for _ in {1..60}; do
  [[ "$(docker inspect "$(compose ps -q app)" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')" == healthy ]] && break
  sleep 2
done
[[ "$(docker inspect "$(compose ps -q app)" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')" == healthy ]] || fail "rollback nao recuperou a aplicacao"

log "Rotacionando a credencial do banco e substituindo a tarefa da aplicacao"
rotated_password="$(openssl rand -hex 24)"
compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' <<< "ALTER ROLE CURRENT_USER PASSWORD '$rotated_password';" >/dev/null
POSTGRES_PASSWORD="$rotated_password" APP_IMAGE="$current_image" compose up --detach --no-deps --force-recreate app >/dev/null
for _ in {1..60}; do
  [[ "$(docker inspect "$(compose ps -q app)" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')" == healthy ]] && break
  sleep 2
done
[[ "$(docker inspect "$(compose ps -q app)" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')" == healthy ]] || fail "aplicacao nao consumiu a credencial rotacionada"

printf '\nDrill aprovado: restore=%s, rollback=%s, rotacao=saudavel.\n' "$source_signature" "$current_image"
