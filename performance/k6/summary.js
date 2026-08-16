const metric = (metrics, name) => metrics[name] || { values: {}, thresholds: {} };
const value = (metrics, name, key, fallback = 0) => metric(metrics, name).values?.[key] ?? fallback;

const thresholdResults = (metrics) => Object.entries(metrics)
  .flatMap(([name, entry]) => Object.entries(entry.thresholds || {})
    .map(([threshold, result]) => ({ metric: name, threshold, passed: result.ok === true })));

export function createLoadTestSummary(data, metadata) {
  const metrics = data.metrics || {};
  const nominalIterations = value(metrics, 'iterations{scenario:nominal}', 'count', value(metrics, 'iterations', 'count'));
  const thresholds = thresholdResults(metrics);

  return {
    schemaVersion: 1,
    executionStatus: 'completed',
    commit: metadata.commit || 'unknown',
    executedAtUtc: metadata.executedAtUtc || 'unknown',
    environment: metadata.environment || 'unspecified',
    resources: metadata.resources || 'unspecified',
    configuration: metadata.configuration,
    observed: {
      nominalRatePerMinute: nominalIterations / 5,
      p99Milliseconds: value(metrics, 'http_req_duration{scenario:nominal}', 'p(99)', value(metrics, 'http_req_duration', 'p(99)')),
      technicalErrorRate: value(metrics, 'technical_error_rate{scenario:nominal}', 'rate', value(metrics, 'technical_error_rate', 'rate')),
      droppedIterations: value(metrics, 'dropped_iterations', 'count'),
      completedEvaluations: value(metrics, 'credit_evaluations_completed{scenario:nominal}', 'count', value(metrics, 'credit_evaluations_completed', 'count')),
    },
    thresholds,
    passed: thresholds.length > 0 && thresholds.every((threshold) => threshold.passed),
    technicalErrorDefinition: 'transport failures (status 0) and HTTP 5xx only; valid 2xx credit decisions are completed evaluations',
  };
}
