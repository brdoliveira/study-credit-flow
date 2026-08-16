import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..');
const scenarioPath = resolve(projectRoot, 'performance/k6/credit-evaluation.js');
const payloadPath = resolve(projectRoot, 'src/test/resources/performance/valid-credit-evaluation.json');

test('AC-046 @spec:AC-046 configura 10.000 avaliações/minuto com p99 e erros técnicos dentro do objetivo', () => {
  const scenario = readFileSync(scenarioPath, 'utf8');
  const payload = JSON.parse(readFileSync(payloadPath, 'utf8'));

  assert.match(scenario, /executor:\s*'constant-arrival-rate'/);
  assert.match(scenario, /rate:\s*10000/);
  assert.match(scenario, /timeUnit:\s*'1m'/);
  assert.match(scenario, /duration:\s*'5m'/);
  assert.match(scenario, /'http_req_duration\{scenario:nominal\}':\s*\['p\(99\)<1000'\]/);
  assert.match(scenario, /'technical_error_rate\{scenario:nominal\}':\s*\['rate<0\.01'\]/);
  assert.match(scenario, /'Idempotency-Key':\s*uuidV4\(\)/);
  assert.equal(payload.creditScore, 800);
  assert.equal(payload.monthlySpending.length, 3);
  assert.ok(payload.availableLimit > 0);
});
