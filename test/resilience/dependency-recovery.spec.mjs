import assert from 'node:assert/strict';
import test from 'node:test';
import { appUrl, browserRequest, compose, loginAsWriter, validCpf, waitFor } from '../e2e/helpers.mjs';

const password = process.env.CREDIT_DEMO_PASSWORD;

async function readiness(expectedStatus) {
  await waitFor(async () => {
    const response = await fetch(`${appUrl}/actuator/health/readiness`);
    assert.equal(response.status, expectedStatus);
  }, 45_000);
}

async function startAndWait(service) {
  await compose(['start', service], 60_000);
  await waitFor(async () => {
    const status = JSON.parse(await compose(['ps', service, '--format', 'json']));
    const item = Array.isArray(status) ? status[0] : status;
    assert.equal(item.Health, 'healthy');
  }, 60_000);
}

async function createEvaluation(jar, csrfToken) {
  const response = await browserRequest(jar, `${appUrl}/api/v1/credit-evaluations`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'Idempotency-Key': crypto.randomUUID(),
      'X-XSRF-TOKEN': csrfToken,
      'X-Correlation-ID': `resilience-${crypto.randomUUID()}`,
    },
    body: JSON.stringify({
      name: 'Teste de resiliência',
      cpf: validCpf(),
      creditScore: 800,
      currentInvoiceAmount: 100,
      totalLimit: 2000,
      availableLimit: 1900,
      latePayments: 0,
      monthlySpending: [100, 110, 120],
    }),
  });
  assert.equal(response.status, 201);
  return response.json();
}

test('dependências degradam de forma observável e recuperam sem perder a outbox', async () => {
  assert.ok(password, 'CREDIT_DEMO_PASSWORD must come from ignored .env or CI');
  const jar = await loginAsWriter(password);
  const session = await browserRequest(jar, `${appUrl}/api/session`);
  const csrfToken = (await session.json()).csrfToken;

  await compose(['stop', 'kafka'], 30_000);
  let evaluation;
  try {
    await readiness(503);
    evaluation = await createEvaluation(jar, csrfToken);
    const status = await compose(['exec', '-T', 'postgres', 'sh', '-c', `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "select status from credit_outbox where evaluation_id = '${evaluation.evaluationId}'"`]);
    assert.notEqual(status, 'PUBLISHED');
  } finally {
    await startAndWait('kafka');
  }

  await readiness(200);
  await waitFor(async () => {
    const status = await compose(['exec', '-T', 'postgres', 'sh', '-c', `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "select status from credit_outbox where evaluation_id = '${evaluation.evaluationId}'"`]);
    assert.equal(status, 'PUBLISHED');
  }, 60_000);

  await compose(['stop', 'postgres'], 30_000);
  try {
    await readiness(503);
    const unavailable = await browserRequest(jar, `${appUrl}/api/v1/credit-evaluations/${evaluation.evaluationId}`);
    assert.ok(unavailable.status >= 500);
  } finally {
    await startAndWait('postgres');
  }

  await readiness(200);
  const recovered = await browserRequest(jar, `${appUrl}/api/v1/credit-evaluations/${evaluation.evaluationId}`);
  assert.equal(recovered.status, 200);

  await compose(['stop', 'keycloak'], 30_000);
  try {
    await assert.rejects(() => loginAsWriter(password));
    const establishedSession = await browserRequest(jar, `${appUrl}/api/session`);
    assert.equal(establishedSession.status, 200);
  } finally {
    await startAndWait('keycloak');
  }

  const recoveredIdentitySession = await browserRequest(await loginAsWriter(password), `${appUrl}/api/session`);
  assert.equal(recoveredIdentitySession.status, 200);
});
