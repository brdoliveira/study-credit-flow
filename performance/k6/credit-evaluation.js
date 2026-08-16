import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { createLoadTestSummary } from './summary.js';

const baseUrl = __ENV.BASE_URL;
const authorization = __ENV.AUTHORIZATION;

if (!baseUrl) {
  fail('BASE_URL is required, for example: http://localhost:8080');
}

const payload = new SharedArray('valid credit evaluation payload', () => [
  JSON.parse(open('../../src/test/resources/performance/valid-credit-evaluation.json')),
])[0];

const technicalErrors = new Rate('technical_error_rate');
const completedEvaluations = new Counter('credit_evaluations_completed');

export const options = {
  discardResponseBodies: true,
  scenarios: {
    warm_up: {
      executor: 'constant-arrival-rate',
      rate: 1000,
      timeUnit: '1m',
      duration: '1m',
      preAllocatedVUs: 50,
      maxVUs: 250,
      tags: { phase: 'warm-up' },
    },
    nominal: {
      executor: 'constant-arrival-rate',
      startTime: '1m',
      rate: 10000,
      timeUnit: '1m',
      duration: '5m',
      preAllocatedVUs: 250,
      maxVUs: 1000,
      tags: { phase: 'nominal' },
    },
  },
  thresholds: {
    'http_req_duration{scenario:nominal}': ['p(99)<1000'],
    'technical_error_rate{scenario:nominal}': ['rate<0.01'],
    'checks{scenario:nominal}': ['rate==1'],
    'iterations{scenario:nominal}': ['count>=50000'],
    dropped_iterations: ['count==0'],
  },
};

function uuidV4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

export default function creditEvaluationLoadTest() {
  const headers = {
    'Content-Type': 'application/json',
    'Idempotency-Key': uuidV4(),
    'X-Correlation-ID': uuidV4(),
  };

  if (authorization) {
    headers.Authorization = authorization;
  }

  const response = http.post(`${baseUrl}/api/v1/credit-evaluations`, JSON.stringify(payload), {
    headers,
    tags: { endpoint: 'credit-evaluation' },
  });

  const isTechnicalError = response.status === 0 || response.status >= 500;
  technicalErrors.add(isTechnicalError);
  completedEvaluations.add(response.status >= 200 && response.status < 300);

  check(response, {
    'credit evaluation completed': (result) => result.status >= 200 && result.status < 300,
  });
}

export function handleSummary(data) {
  const summary = createLoadTestSummary(data, {
    commit: __ENV.LOAD_TEST_COMMIT,
    executedAtUtc: __ENV.LOAD_TEST_EXECUTED_AT_UTC,
    environment: __ENV.LOAD_TEST_ENVIRONMENT,
    resources: __ENV.LOAD_TEST_RESOURCES,
    configuration: {
      baseUrl,
      nominalRatePerMinute: 10000,
      nominalDuration: '5m',
      warmUpRatePerMinute: 1000,
      warmUpDuration: '1m',
    },
  });
  const evidenceFile = __ENV.LOAD_TEST_EVIDENCE_FILE || 'docs/evidence/load-test-summary.json';
  const rendered = `${JSON.stringify(summary, null, 2)}\n`;

  return { [evidenceFile]: rendered, stdout: rendered };
}
