import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { createLoadTestSummary } from '../../../../performance/k6/summary.js';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..');
const scenarioPath = resolve(projectRoot, 'performance/k6/credit-evaluation.js');
const payloadPath = resolve(projectRoot, 'src/test/resources/performance/valid-credit-evaluation.json');
const summaryPath = resolve(projectRoot, 'performance/k6/summary.js');
const runnerPath = resolve(projectRoot, 'scripts/run-load-test.ps1');
const evidencePath = resolve(projectRoot, 'docs/evidence/load-test-summary.json');

test('AC-046 AC-068 @spec:AC-046 @spec:AC-068 configura a fase nominal com 10.000 avaliações/minuto e seus thresholds', () => {
  const scenario = readFileSync(scenarioPath, 'utf8');
  const payload = JSON.parse(readFileSync(payloadPath, 'utf8'));

  assert.match(scenario, /executor:\s*'constant-arrival-rate'/);
  assert.match(scenario, /rate:\s*10000/);
  assert.match(scenario, /timeUnit:\s*'1m'/);
  assert.match(scenario, /duration:\s*'5m'/);
  assert.match(scenario, /'http_req_duration\{scenario:nominal\}':\s*\['p\(99\)<1000'\]/);
  assert.match(scenario, /'technical_error_rate\{scenario:nominal\}':\s*\['rate<0\.01'\]/);
  assert.match(scenario, /'iterations\{scenario:nominal\}':\s*\['count>=50000'\]/);
  assert.match(scenario, /dropped_iterations:\s*\['count==0'\]/);
  assert.match(scenario, /'Idempotency-Key':\s*uuidV4\(\)/);
  assert.equal(payload.creditScore, 800);
  assert.equal(payload.monthlySpending.length, 3);
  assert.ok(payload.availableLimit > 0);

  const summary = createLoadTestSummary({ metrics: {
    'iterations{scenario:nominal}': { values: { count: 50000 } },
    'http_req_duration{scenario:nominal}': { values: { 'p(99)': 999 }, thresholds: { 'p(99)<1000': { ok: true } } },
    'technical_error_rate{scenario:nominal}': { values: { rate: 0.009 }, thresholds: { 'rate<0.01': { ok: true } } },
    dropped_iterations: { values: { count: 0 }, thresholds: { 'count==0': { ok: true } } },
  } }, { configuration: {} });
  assert.equal(summary.observed.nominalRatePerMinute, 10000);
  assert.equal(summary.observed.p99Milliseconds, 999);
  assert.equal(summary.observed.technicalErrorRate, 0.009);
  assert.equal(summary.observed.droppedIterations, 0);
  assert.equal(summary.passed, true);
});

test('AC-069 @spec:AC-069 registra evidência rastreável e sanitizada a partir do resumo do k6', () => {
  const summary = readFileSync(summaryPath, 'utf8');
  const runner = readFileSync(runnerPath, 'utf8');
  const placeholder = JSON.parse(readFileSync(evidencePath, 'utf8'));

  for (const field of ['commit', 'executedAtUtc', 'environment', 'resources', 'nominalRatePerMinute', 'p99Milliseconds', 'technicalErrorRate', 'droppedIterations', 'thresholds']) {
    assert.match(summary, new RegExp(field));
  }
  assert.match(runner, /LOAD_TEST_COMMIT/);
  assert.match(runner, /LOAD_TEST_EXECUTED_AT_UTC/);
  assert.match(runner, /Remove-Item Env:AUTHORIZATION/);
  assert.match(runner, /if \(\$LASTEXITCODE -ne 0\)/);
  assert.equal(placeholder.executionStatus, 'not-executed');
  assert.doesNotMatch(JSON.stringify(placeholder), /\b\d{11}\b|Bearer\s+|token["']\s*:/i);
});

test('AC-070 @spec:AC-070 contabiliza apenas transporte e respostas 5xx como erro técnico', () => {
  const scenario = readFileSync(scenarioPath, 'utf8');

  assert.match(scenario, /response\.status === 0 \|\| response\.status >= 500/);
  assert.match(scenario, /completedEvaluations\.add\(response\.status >= 200 && response\.status < 300\)/);
  assert.match(scenario, /result\.status >= 200 && result\.status < 300/);
});
