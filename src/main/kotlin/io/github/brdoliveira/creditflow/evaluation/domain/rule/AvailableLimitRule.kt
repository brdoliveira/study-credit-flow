package io.github.brdoliveira.creditflow.evaluation.domain.rule

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus
import java.math.BigDecimal

/** Bloqueia avaliações sem limite disponível. */
class AvailableLimitRule : CreditRule {
    override val code = "AVAILABLE_LIMIT"
    override val name = "Limite disponível"
    override val severity = RuleSeverity.BLOCKING

    /** Verifica se há saldo disponível para concessão. */
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
