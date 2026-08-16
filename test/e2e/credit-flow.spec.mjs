import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { appUrl, browserRequest, compose, loginAsWriter, validCpf, waitFor } from './helpers.mjs';

const password = process.env.CREDIT_DEMO_PASSWORD;
const correlationId = 'e2e-compose-correlation';
const requireRuntime = () => assert.ok(password, 'CREDIT_DEMO_PASSWORD must come from ignored .env');

async function authenticatedEvaluation() {
  requireRuntime();
  const jar = await loginAsWriter(password);
  const session = await browserRequest(jar, `${appUrl}/api/session`, { headers: { accept: 'application/json' } });
  const csrf = (await session.json()).csrfToken;
  const created = await browserRequest(jar, `${appUrl}/api/v1/credit-evaluations`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'Idempotency-Key': crypto.randomUUID(), 'X-XSRF-TOKEN': csrf, 'X-Correlation-ID': correlationId },
    body: JSON.stringify({ name: 'Operador demo', cpf: validCpf(), creditScore: 800, currentInvoiceAmount: 100, totalLimit: 2000, availableLimit: 1900, latePayments: 0, monthlySpending: [100, 110, 120] }),
  });
  assert.equal(created.status, 201);
  return { jar, evaluation: await created.json() };
}

test('@spec:AC-052 issuer publico usa JWKS interno e autentica a sessao Compose', async () => {
  requireRuntime();
  const jar = await loginAsWriter(password);
  const session = await browserRequest(jar, `${appUrl}/api/session`);
  assert.equal(session.status, 200);
  assert.ok((await session.json()).authorities.includes('SCOPE_credit:write'));
});

test('@spec:AC-060 evento percorre Outbox Kafka e consumidor sem dados sensiveis', async () => {
  const { evaluation } = await authenticatedEvaluation();
  const event = await waitFor(async () => {
    const raw = await compose(['exec', '-T', 'postgres', 'sh', '-c', `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "select payload::text from credit_outbox where evaluation_id = '${evaluation.evaluationId}'"`]);
    assert.ok(raw); return JSON.parse(raw);
  });
  assert.equal(event.correlationId, correlationId);
  assert.doesNotMatch(JSON.stringify(event), /cpf|token|password|stacktrace|exception/i);
  const broker = await waitFor(() => compose(['exec', '-T', 'kafka', 'rpk', 'topic', 'consume', 'credit.evaluation.completed.v1', '--num', '1', '--offset', 'start', '--format', '%v']));
  assert.match(broker, new RegExp(evaluation.evaluationId));
  await waitFor(async () => assert.equal(await compose(['exec', '-T', 'postgres', 'sh', '-c', `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "select count(*) from processed_credit_evaluation_event where event_id = '${evaluation.evaluationId}'"`]), '1'));
});

test('@spec:AC-065 Compose limpo deixa app PostgreSQL Keycloak e Kafka saudaveis', async () => {
  const status = JSON.parse(await compose(['ps', '--format', 'json']));
  for (const service of ['app', 'postgres', 'keycloak', 'kafka']) assert.equal(status.find(item => item.Service === service)?.Health, 'healthy');
});

test('@spec:AC-066 jornada visual autenticada cria consulta e baixa PDF', async () => {
  const { jar, evaluation } = await authenticatedEvaluation();
  const page = await (await browserRequest(jar, `${appUrl}/api/v1/credit-evaluations`)).json();
  assert.ok(page.items.some(item => item.evaluationId === evaluation.evaluationId));
  assert.ok(evaluation.rules.length > 0);
  const pdf = await browserRequest(jar, `${appUrl}/api/v1/credit-evaluations/report.pdf`);
  assert.equal(pdf.headers.get('content-type'), 'application/pdf');
  assert.deepEqual([...new Uint8Array((await pdf.arrayBuffer()).slice(0, 4))], [37, 80, 68, 70]);
});

test('@spec:AC-067 execucao local usa segredos ignorados e nenhuma fixture com CPF completo', async () => {
  const root = new URL('../../', import.meta.url);
  const content = (await Promise.all(['.gitignore', '.env.example', 'compose.yaml', 'docker/keycloak/realm-export.json'].map(path => readFile(new URL(path, root), 'utf8')))).join('\n');
  assert.match(content, /^\.env$/m);
  assert.match(content, /CREDIT_DEMO_PASSWORD: \$\{CREDIT_DEMO_PASSWORD\}/);
  assert.doesNotMatch(content, /(?<!\d)\d{11}(?!\d)/);
});
