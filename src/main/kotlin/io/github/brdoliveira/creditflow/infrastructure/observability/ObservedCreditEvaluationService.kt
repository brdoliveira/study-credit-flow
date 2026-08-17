package io.github.brdoliveira.creditflow.infrastructure.observability

import io.github.brdoliveira.creditflow.infrastructure.web.IdempotentCreditEvaluationResponse
import org.springframework.stereotype.Component
import java.time.Duration

/** Records bounded-cardinality metrics only for newly processed evaluations. */
@Component
class ObservedCreditEvaluationService(
    private val metrics: CreditMetrics,
) {
    fun observe(operation: () -> IdempotentCreditEvaluationResponse): IdempotentCreditEvaluationResponse {
        val startedAt = System.nanoTime()
        val outcome = operation()
        if (!outcome.replayed) {
            metrics.recordEvaluation(outcome.response.decision, Duration.ofNanos(System.nanoTime() - startedAt))
            outcome.response.rules
                .filter { it.status == "FAILED" }
                .forEach { metrics.recordRuleFailure(it.code) }
        }
        return outcome
    }
}
