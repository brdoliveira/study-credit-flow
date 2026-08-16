package com.itau.credit.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

class CreditMetrics(private val registry: MeterRegistry) {
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

    fun recordTechnicalError(type: String) {
        require(type in ERROR_TYPES) { "Unsupported error metric tag" }
        registry.counter("credit.evaluation.errors", "type", type).increment()
    }

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
