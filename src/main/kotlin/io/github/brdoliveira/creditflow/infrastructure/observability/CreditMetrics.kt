package io.github.brdoliveira.creditflow.infrastructure.observability

import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationMetrics
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

/** Registra métricas com conjuntos limitados de tags. */
class CreditMetrics(private val registry: MeterRegistry) : CreditEvaluationMetrics {
    /** Registra uma avaliação recém-processada e suas regras reprovadas. */
    override fun record(evaluation: CreditEvaluation, duration: Duration) {
        recordEvaluation(evaluation.decision.name, duration)
        evaluation.ruleResults
            .filter { it.status.name == "FAILED" }
            .forEach { recordRuleFailure(it.code) }
    }

    /** Registra throughput e duração da avaliação. */
    fun recordEvaluation(decision: String, duration: Duration) {
        require(decision in DECISIONS) { "Unsupported decision metric tag" }
        Counter.builder("credit.evaluations")
            .description("Credit evaluations processed; also represents throughput")
            .tag("decision", decision)
            .register(registry)
            .increment()
        Timer.builder("credit.evaluation.duration")
            .description("End-to-end credit evaluation duration")
            .register(registry)
            .record(duration)
    }

    /** Registra uma falha técnica de categoria controlada. */
    fun recordTechnicalError(type: String) {
        require(type in ERROR_TYPES) { "Unsupported error metric tag" }
        registry.counter("credit.evaluation.errors", "type", type).increment()
    }

    /** Registra a reprovação de uma regra conhecida. */
    fun recordRuleFailure(ruleCode: String) {
        require(ruleCode in RULE_CODES) { "Unsupported rule metric tag" }
        registry.counter("credit.rule.failures", "rule", ruleCode).increment()
    }

    companion object {
        private val DECISIONS = setOf("APPROVED", "REJECTED")
        private val ERROR_TYPES = setOf("INTERNAL", "DEPENDENCY")
        private val RULE_CODES = setOf("MINIMUM_SCORE", "MAX_LATE_PAYMENTS", "AVAILABLE_LIMIT", "LIMIT_COMMITMENT", "SPENDING_TREND")
    }
}
