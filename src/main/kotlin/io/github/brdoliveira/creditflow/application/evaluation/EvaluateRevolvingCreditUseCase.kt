package io.github.brdoliveira.creditflow.application.evaluation

import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Boundary to the rule module. The rule engine must evaluate every active rule
 * before returning so the application layer can persist an explainable decision.
 */
fun interface CreditRuleEvaluator {
    fun evaluate(command: EvaluateCreditCommand): RuleEvaluation
}

data class RuleEvaluation(
    val ruleSetVersion: String,
    val executedRules: List<ExecutedRule>,
)

fun interface RevolvingCreditCalculator {
    fun calculate(command: EvaluateCreditCommand): BigDecimal
}

/** Transactional boundary; its implementation persists the decision photograph. */
interface CreditEvaluationSnapshotStore {
    fun save(snapshot: CreditEvaluationSnapshot): CreditEvaluationSnapshot
}

class EvaluateRevolvingCreditUseCase(
    private val ruleEvaluator: CreditRuleEvaluator,
    private val creditCalculator: RevolvingCreditCalculator,
    private val snapshotStore: CreditEvaluationSnapshotStore,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun execute(command: EvaluateCreditCommand): CreditEvaluationResult {
        val startedAt = clock.instant()
        val ruleEvaluation = ruleEvaluator.evaluate(command)
        val decision = if (ruleEvaluation.executedRules.any(::isBlockingFailure)) {
            CreditDecision.REJECTED
        } else {
            CreditDecision.APPROVED
        }
        val approvedAmount = if (decision == CreditDecision.APPROVED) {
            creditCalculator.calculate(command)
        } else {
            BigDecimal.ZERO
        }
        val completedAt = clock.instant()
        val snapshot = CreditEvaluationSnapshot(
            evaluationId = idGenerator(),
            maskedCpf = command.cpf.maskedCpf(),
            decision = decision,
            approvedAmount = approvedAmount,
            ruleSetVersion = ruleEvaluation.ruleSetVersion,
            executedRules = ruleEvaluation.executedRules.toList(),
            processedAt = completedAt,
            processingTimeMs = Duration.between(startedAt, completedAt).toMillis(),
            correlationId = command.correlationId,
        )

        return CreditEvaluationResult.from(snapshotStore.save(snapshot))
    }

    private fun isBlockingFailure(rule: ExecutedRule) =
        rule.severity == RuleSeverity.BLOCKING && rule.status == RuleStatus.FAILED
}

private fun String.maskedCpf(): String {
    val digits = filter(Char::isDigit)
    require(digits.length == 11) { "CPF must contain 11 digits" }
    return "***.***.***-${digits.takeLast(2)}"
}
