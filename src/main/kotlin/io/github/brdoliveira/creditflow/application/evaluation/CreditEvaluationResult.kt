package io.github.brdoliveira.creditflow.application.evaluation

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class CreditDecision {
    APPROVED,
    REJECTED,
}

enum class RuleSeverity {
    BLOCKING,
    WARNING,
}

enum class RuleStatus {
    PASSED,
    FAILED,
    WARNING,
}

data class ExecutedRule(
    val code: String,
    val name: String,
    val severity: RuleSeverity,
    val status: RuleStatus,
    val reason: String,
)

/** Immutable audit record. It deliberately contains only the masked CPF. */
data class CreditEvaluationSnapshot(
    val evaluationId: UUID,
    val maskedCpf: String,
    val decision: CreditDecision,
    val approvedAmount: BigDecimal,
    val ruleSetVersion: String,
    val executedRules: List<ExecutedRule>,
    val processedAt: Instant,
    val processingTimeMs: Long,
    val correlationId: String,
)

data class CreditEvaluationResult(
    val evaluationId: UUID,
    val maskedCpf: String,
    val decision: CreditDecision,
    val approvedAmount: BigDecimal,
    val ruleSetVersion: String,
    val executedRules: List<ExecutedRule>,
    val processedAt: Instant,
    val processingTimeMs: Long,
    val correlationId: String,
) {
    companion object {
        fun from(snapshot: CreditEvaluationSnapshot) = CreditEvaluationResult(
            evaluationId = snapshot.evaluationId,
            maskedCpf = snapshot.maskedCpf,
            decision = snapshot.decision,
            approvedAmount = snapshot.approvedAmount,
            ruleSetVersion = snapshot.ruleSetVersion,
            executedRules = snapshot.executedRules,
            processedAt = snapshot.processedAt,
            processingTimeMs = snapshot.processingTimeMs,
            correlationId = snapshot.correlationId,
        )
    }
}
