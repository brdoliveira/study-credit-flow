import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(path, 'utf8');

test('local observability stack is authenticated, pinned, provisioned and loopback-only', () => {
  const compose = read('compose.observability.yaml');
  const prometheus = read('observability/prometheus/prometheus.yml');
  const collector = read('observability/otel-collector/config.yml');
  const loki = read('observability/loki/loki.yml');
  const datasources = read('observability/grafana/provisioning/datasources/datasources.yml');
  const dashboard = JSON.parse(read('observability/grafana/dashboards/credit-flow.json'));

  for (const service of ['prometheus', 'alertmanager', 'otel-collector', 'tempo', 'loki', 'grafana']) {
    assert.match(compose, new RegExp(`^  ${service}:`, 'm'));
  }
  for (const image of ['prom/prometheus:v3.12.0', 'prom/alertmanager:v0.33.1', 'otel/opentelemetry-collector-contrib:0.153.0', 'grafana/tempo:2.10.7', 'grafana/loki:3.7.3', 'grafana/grafana:13.1.0']) {
    assert.ok(compose.includes(image), `image must be pinned: ${image}`);
  }
  assert.equal((compose.match(/127\.0\.0\.1:\$\{/g) ?? []).length, 5);
  assert.match(prometheus, /oauth2:[\s\S]*client_secret_file:[\s\S]*token_url:/);
  assert.doesNotMatch(prometheus, /client_secret:\s*[^_]/);
  assert.match(collector, /otlp_http\/loki:[\s\S]*endpoint: http:\/\/loki:3100\/otlp/);
  assert.match(collector, /logs:[\s\S]*receivers: \[otlp\][\s\S]*exporters: \[otlp_http\/loki\]/);
  assert.match(loki, /schema: v13/);
  assert.match(loki, /retention_period: 168h/);
  assert.match(loki, /allow_structured_metadata: true/);
  assert.match(datasources, /uid: prometheus[\s\S]*uid: tempo[\s\S]*uid: loki/);
  assert.match(datasources, /tracesToLogsV2:[\s\S]*datasourceUid: loki/);
  assert.match(datasources, /derivedFields:[\s\S]*datasourceUid: tempo/);
  assert.equal(dashboard.uid, 'credit-flow-operation');
  assert.ok(dashboard.panels.some((panel) => panel.datasource?.uid === 'loki'));
});

test('alerts cover SLOs, readiness and asynchronous processing with runbooks', () => {
  const alerts = read('observability/prometheus/alerts.yml');
  const runbooks = read('docs/observability.md');
  for (const alert of ['CreditFlowUnavailable', 'CreditFlowErrorBudgetBurn', 'CreditFlowP99LatencyHigh', 'CreditFlowOutboxTerminalFailure', 'CreditFlowOutboxBacklog', 'CreditFlowKafkaConsumptionFailures']) {
    assert.match(alerts, new RegExp(`alert: ${alert}`));
    assert.match(runbooks, new RegExp(`### ${alert}`));
  }
  assert.match(alerts, /\[5m\][\s\S]*\[1h\]/);
  assert.match(alerts, /sum\(rate\(http_server_requests_seconds_count[\s\S]*> 0\.1/);
  assert.match(read('scripts/observability.sh'), /render-alert-rules\.mjs --check/);
});

test('application exports OpenTelemetry and bounded asynchronous metrics', () => {
  const build = read('build.gradle.kts');
  const config = read('src/main/resources/application-observability.yml');
  const logback = read('src/main/resources/logback-spring.xml');
  const initializer = read('src/main/kotlin/io/github/brdoliveira/creditflow/platform/observability/OpenTelemetryLogbackInitializer.kt');
  const metrics = read('src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/observability/MicrometerAsyncProcessingMetrics.kt');
  assert.match(build, /spring-boot-starter-opentelemetry/);
  assert.match(build, /opentelemetry-logback-appender-1\.0/);
  assert.match(config, /OTEL_TRACING_ENABLED/);
  assert.match(config, /OTEL_METRICS_ENABLED:false/);
  assert.match(config, /OTEL_LOGGING_ENABLED:false/);
  assert.match(config, /OTEL_EXPORTER_OTLP_LOGS_ENDPOINT/);
  assert.match(config, /percentiles-histogram/);
  assert.match(logback, /OpenTelemetryAppender/);
  assert.match(logback, /captureMdcAttributes>correlationId,traceId,spanId/);
  assert.doesNotMatch(logback, /captureMdcAttributes>[^<]*(cpf|token|amount|requestBody|payload)/i);
  assert.match(initializer, /OpenTelemetryAppender\.install\(openTelemetry\)/);
  assert.match(metrics, /OutboxOutcome\.entries/);
  assert.match(metrics, /KafkaOutcome\.entries/);
});
