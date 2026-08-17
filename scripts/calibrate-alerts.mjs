import { mkdir, readFile, writeFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const thresholdsUrl = new URL('observability/prometheus/alert-thresholds.json', root);
const evidenceUrl = new URL('.context/alert-calibration.json', root);
const prometheusUrl = process.env.PROMETHEUS_URL ?? 'http://localhost:9090';
const days = Number(process.env.ALERT_CALIBRATION_DAYS ?? 7);
const apply = process.argv.includes('--apply');
const end = Math.floor(Date.now() / 1000);
const start = end - days * 86400;

const queries = {
  requestRate: 'sum(rate(http_server_requests_seconds_count{job="credit-flow"}[5m]))',
  p99LatencySeconds: 'histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{job="credit-flow"}[5m])))',
  outboxBacklogCount: 'credit_outbox_backlog{job="credit-flow"}',
  outboxOldestAgeSeconds: 'credit_outbox_oldest_pending_age_seconds{job="credit-flow"}',
};

async function range(query) {
  const url = new URL('/api/v1/query_range', prometheusUrl);
  url.search = new URLSearchParams({ query, start: String(start), end: String(end), step: '300' });
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Prometheus returned ${response.status} for ${query}`);
  const payload = await response.json();
  if (payload.status !== 'success') throw new Error(`Prometheus query failed: ${JSON.stringify(payload)}`);
  return payload.data.result.flatMap(series => series.values.map(([, value]) => Number(value))).filter(Number.isFinite);
}

function percentile(values, ratio) {
  if (!values.length) return 0;
  const sorted = values.toSorted((left, right) => left - right);
  return sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * ratio))];
}

const samples = Object.fromEntries(await Promise.all(Object.entries(queries).map(async ([name, query]) => [name, await range(query)])));
if (!samples.requestRate.length) throw new Error('No HTTP traffic was found in the calibration window');
const current = JSON.parse(await readFile(thresholdsUrl, 'utf8'));
const observed = Object.fromEntries(Object.entries(samples).map(([name, values]) => [name, {
  samples: values.length,
  p95: percentile(values, 0.95),
  peak: Math.max(...values, 0),
}]));
const suggested = {
  ...current,
  minimumRequestRate: Math.max(0.01, Number((observed.requestRate.p95 * 0.05).toFixed(2))),
  p99LatencySeconds: Math.max(current.p99LatencySeconds, Number((observed.p99LatencySeconds.p95 * 1.2).toFixed(2))),
  outboxBacklogCount: Math.max(current.outboxBacklogCount, Math.ceil(observed.outboxBacklogCount.p95 * 1.5)),
  outboxOldestAgeSeconds: Math.max(current.outboxOldestAgeSeconds, Math.ceil(observed.outboxOldestAgeSeconds.p95 * 1.5)),
};
const evidence = {
  generatedAt: new Date().toISOString(),
  window: { days, start: new Date(start * 1000).toISOString(), end: new Date(end * 1000).toISOString() },
  source: prometheusUrl,
  observed,
  current,
  suggested,
  applied: apply,
  note: 'Error-budget ratios remain tied to the service SLO and are never changed automatically.',
};

await mkdir(new URL('.context/', root), { recursive: true });
await writeFile(evidenceUrl, `${JSON.stringify(evidence, null, 2)}\n`);
if (apply) {
  await writeFile(thresholdsUrl, `${JSON.stringify(suggested, null, 2)}\n`);
  await import('./render-alert-rules.mjs');
}
console.log(JSON.stringify(evidence, null, 2));
