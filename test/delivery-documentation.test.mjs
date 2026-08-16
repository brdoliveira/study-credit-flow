import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const read = (relativePath) => readFile(new URL(`../${relativePath}`, import.meta.url), 'utf8');
const [readme, evidenceIndex, architecture, aiUsage, demoScript, envExample] = await Promise.all([
  read('README.md'),
  read('docs/evidence/README.md'),
  read('docs/architecture.md'),
  read('docs/ai-usage.md'),
  read('scripts/demo.ps1'),
  read('.env.example'),
]);

test('@spec:AC-065 roteiro documenta Compose limpo e serviços saudáveis', () => {
  assert.match(readme, /scripts\/e2e-compose\.ps1/);
  assert.match(readme, /volumes limpos/i);
  assert.match(readme, /docker compose ps/);
  assert.match(evidenceIndex, /Compose limpo e jornada E2E/);
});

test('@spec:AC-066 roteiro conduz jornada visual ponta a ponta', () => {
  for (const expectedStep of ['Keycloak', 'avaliação', 'histórico', 'PDF', 'evento', 'métricas']) {
    assert.match(readme, new RegExp(expectedStep, 'i'));
  }
  assert.match(demoScript, /readiness/);
  assert.match(demoScript, /http:\/\/localhost:8080/);
});

test('@spec:AC-067 @principle:P-002 execução local usa segredos locais e não versionados', () => {
  assert.match(readme, /\.env\.example/);
  assert.match(readme, /ignorado pelo Git/i);
  assert.match(demoScript, /Test-Path '.env'/);
  assert.match(envExample, /replace-with-a-local-password/);
  assert.doesNotMatch(evidenceIndex, /(access_token|BEGIN PRIVATE KEY|CREDIT_DEMO_PASSWORD=.{12,})/);
});

test('@spec:AC-078 README conduz demonstração e diagnóstico completos', () => {
  for (const expectedInstruction of ['Copy-Item .env.example .env', 'docker compose up --build', 'docker compose down', 'docker compose logs']) {
    assert.ok(readme.includes(expectedInstruction), `README must include: ${expectedInstruction}`);
  }
  assert.match(readme, /problemas conhecidos|Em caso de falha/i);
});

test('@spec:AC-079 índice identifica comando, resultado, data e revisão validada', () => {
  for (const heading of ['Comando', 'Resultado a registrar', 'Data (UTC)', 'Commit validado']) {
    assert.ok(evidenceIndex.includes(heading), `evidence index must include: ${heading}`);
  }
  assert.match(evidenceIndex, /Arquitetura proposta, não evidência de execução/);
  assert.match(architecture, /Evolução para AWS/);
  assert.match(aiUsage, /não foram inventados/i);
});
