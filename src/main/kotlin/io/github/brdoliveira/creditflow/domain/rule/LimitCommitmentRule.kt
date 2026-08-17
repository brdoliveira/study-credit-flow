package io.github.brdoliveira.creditflow.domain.rule

import io.github.brdoliveira.creditflow.domain.model.CreditEvaluationContext
import io.github.brdoliveira.creditflow.domain.model.RuleResult
import io.github.brdoliveira.creditflow.domain.model.RuleSeverity
import io.github.brdoliveira.creditflow.domain.model.RuleStatus
import java.math.BigDecimal
import java.math.MathContext

class LimitCommitmentRule(private val maximumCommitment: BigDecimal = BigDecimal("0.80")) : CreditRule {
    init {
        require(maximumCommitment > BigDecimal.ZERO && maximumCommitment <= BigDecimal.ONE)
    }

    override val code = "LIMIT_COMMITMENT"
    override val name = "Comprometimento do limite"
    override val severity = RuleSeverity.BLOCKING

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
