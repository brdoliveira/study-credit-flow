import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFile(new URL(path, root), 'utf8');

test('gate de sistema cobre browser, acessibilidade, dependencias e recuperacao', async () => {
  const [system, recovery, resilience, browser, docs] = await Promise.all([
    read('scripts/system-tests.sh'),
    read('scripts/recovery-drill.sh'),
    read('test/resilience/dependency-recovery.spec.mjs'),
    read('test/browser/credit-flow.spec.mjs'),
    read('docs/recovery.md'),
  ]);
  assert.match(system, /test:e?browser|test:browser/);
  assert.match(system, /dependency-recovery\.spec\.mjs/);
  assert.match(system, /recovery-drill\.sh/);
  for (const service of ['kafka', 'postgres', 'keycloak']) assert.match(resilience, new RegExp(`stop', '${service}`));
  assert.match(resilience, /status, 'PUBLISHED'/);
  assert.match(browser, /AxeBuilder/);
  assert.match(browser, /wcag2aa/);
  assert.match(recovery, /pg_dump/);
  assert.match(recovery, /pg_restore/);
  assert.match(recovery, /busybox:1\.36\.1/);
  assert.match(recovery, /ALTER ROLE CURRENT_USER PASSWORD/);
  assert.match(recovery, /credit-flow-system-\*/);
  assert.match(docs, /RPO\/RTO/);
  assert.match(docs, /expand\/contract/);
});
