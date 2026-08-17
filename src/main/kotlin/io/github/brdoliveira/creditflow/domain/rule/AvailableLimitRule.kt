package io.github.brdoliveira.creditflow.domain.rule

import io.github.brdoliveira.creditflow.domain.model.CreditEvaluationContext
import io.github.brdoliveira.creditflow.domain.model.RuleResult
import io.github.brdoliveira.creditflow.domain.model.RuleSeverity
import io.github.brdoliveira.creditflow.domain.model.RuleStatus
import java.math.BigDecimal

class AvailableLimitRule : CreditRule {
    override val code = "AVAILABLE_LIMIT"
    override val name = "Limite disponível"
    override val severity = RuleSeverity.BLOCKING

    override fun evaluate(context: CreditEvaluationContext): RuleResult {
        val passed = context.availableLimit > BigDecimal.ZERO
        return RuleResult(
            code,
            name,
            severity,
            if (passed) RuleStatus.PASSED else RuleStatus.FAILED,
            if (passed) "Cliente possui limite disponível" else "Cliente não possui limite disponível",
            mapOf("availableLimit" to context.availableLimit.toPlainString()),
        )
    }
}
