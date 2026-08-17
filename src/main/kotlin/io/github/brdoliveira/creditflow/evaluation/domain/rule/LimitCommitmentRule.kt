package io.github.brdoliveira.creditflow.evaluation.domain.rule

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus
import java.math.BigDecimal
import java.math.MathContext

/** Bloqueia faturas que comprometem parcela excessiva do limite total. */
class LimitCommitmentRule(private val maximumCommitment: BigDecimal = BigDecimal("0.80")) : CreditRule {
    init {
        require(maximumCommitment > BigDecimal.ZERO && maximumCommitment <= BigDecimal.ONE)
    }

    override val code = "LIMIT_COMMITMENT"
    override val name = "Comprometimento do limite"
    override val severity = RuleSeverity.BLOCKING

    /** Calcula e valida a proporção comprometida do limite. */
    override fun evaluate(context: CreditEvaluationContext): RuleResult {
        val commitment = context.currentInvoiceAmount.divide(context.totalLimit, MathContext.DECIMAL64)
        val passed = commitment <= maximumCommitment
        return RuleResult(
            code,
            name,
            severity,
            if (passed) RuleStatus.PASSED else RuleStatus.FAILED,
            if (passed) "Comprometimento dentro do máximo" else "Comprometimento excede o máximo",
            mapOf("commitment" to commitment.toPlainString(), "maximum" to maximumCommitment.toPlainString()),
        )
    }
}
