package com.itau.credit.domain.rule

import com.itau.credit.domain.model.CreditEvaluationContext
import com.itau.credit.domain.model.RuleResult
import com.itau.credit.domain.model.RuleSeverity
import com.itau.credit.domain.model.RuleStatus

class MaxLatePaymentsRule(private val maximumLatePayments: Int = 2) : CreditRule {
    init {
        require(maximumLatePayments >= 0)
    }

    override val code = "MAX_LATE_PAYMENTS"
    override val name = "Quantidade máxima de atrasos"
    override val severity = RuleSeverity.BLOCKING

    override fun evaluate(context: CreditEvaluationContext): RuleResult {
        val passed = context.latePayments <= maximumLatePayments
        return RuleResult(
            code,
            name,
            severity,
            if (passed) RuleStatus.PASSED else RuleStatus.FAILED,
            if (passed) "Atrasos dentro do máximo configurado" else "Quantidade de atrasos excede o máximo",
            mapOf("latePayments" to context.latePayments.toString(), "maximum" to maximumLatePayments.toString()),
        )
    }
}
