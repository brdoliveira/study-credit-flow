import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

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
  completedEvaluations.add(1);

  check(response, {
    'credit evaluation is created': (result) => result.status === 201,
  });
}
