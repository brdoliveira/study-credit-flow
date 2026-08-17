#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif docker-compose version >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  printf 'Docker Compose is required.\n' >&2
  exit 1
fi

COMPOSE_FILES=(-f compose.yaml -f compose.observability.yaml)

require_environment() {
  if [[ ! -f .env ]]; then
    printf 'Missing .env. Copy .env.example and replace every local password.\n' >&2
    exit 1
  fi
  local variable
  for variable in PROMETHEUS_CLIENT_SECRET GRAFANA_ADMIN_PASSWORD; do
    if ! grep -Eq "^${variable}=.{12,}$" .env || grep -Eq "^${variable}=replace-with" .env; then
      printf 'Set a non-placeholder value for %s in .env.\n' "$variable" >&2
      exit 1
    fi
  done
  if ! grep -Eq '^GRAFANA_ADMIN_USER=.+$' .env; then
    printf 'Set GRAFANA_ADMIN_USER in .env.\n' >&2
    exit 1
  fi
}

validate() {
  require_environment
  node scripts/render-alert-rules.mjs --check
  "${COMPOSE[@]}" "${COMPOSE_FILES[@]}" config --quiet
  mkdir -p .context
  touch .context/prometheus-test-secret
  docker run --rm --entrypoint /bin/promtool \
    -v "$ROOT_DIR/observability/prometheus:/etc/prometheus:ro" \
    -v "$ROOT_DIR/.context/prometheus-test-secret:/tmp/client-secret:ro" \
    prom/prometheus:v3.12.0 check config /etc/prometheus/prometheus.yml
  docker run --rm --entrypoint /bin/amtool \
    -v "$ROOT_DIR/observability/alertmanager:/etc/alertmanager:ro" \
    prom/alertmanager:v0.33.1 check-config /etc/alertmanager/alertmanager.yml
  docker run --rm \
    -v "$ROOT_DIR/observability/otel-collector/config.yml:/etc/otelcol-contrib/config.yml:ro" \
    otel/opentelemetry-collector-contrib:0.153.0 validate --config=/etc/otelcol-contrib/config.yml
  docker run --rm \
    -v "$ROOT_DIR/observability/tempo/tempo.yml:/etc/tempo/tempo.yml:ro" \
    grafana/tempo:2.10.7 -config.file=/etc/tempo/tempo.yml -config.verify=true
  jq empty observability/grafana/dashboards/credit-flow.json
}

case "${1:-}" in
  validate)
    validate
    ;;
  start)
    require_environment
    ./gradlew bootJar --no-daemon
    "${COMPOSE[@]}" "${COMPOSE_FILES[@]}" up -d --build
    "${COMPOSE[@]}" "${COMPOSE_FILES[@]}" ps
    printf 'Grafana: http://localhost:%s\n' "${GRAFANA_PORT:-3000}"
    printf 'Prometheus: http://localhost:%s\n' "${PROMETHEUS_PORT:-9090}"
    printf 'Alertmanager: http://localhost:%s\n' "${ALERTMANAGER_PORT:-9093}"
    ;;
  stop)
    "${COMPOSE[@]}" "${COMPOSE_FILES[@]}" down
    ;;
  status)
    "${COMPOSE[@]}" "${COMPOSE_FILES[@]}" ps
    ;;
  *)
    printf 'Usage: %s {validate|start|stop|status}\n' "$0" >&2
    exit 2
    ;;
esac
