package com.itau.credit.domain.rule

import com.itau.credit.domain.model.CreditEvaluationContext
import com.itau.credit.domain.model.RuleResult
import com.itau.credit.domain.model.RuleSeverity
import com.itau.credit.domain.model.RuleStatus
import java.math.BigDecimal
import java.math.MathContext

class RecentSpendingTrendRule(private val warningThreshold: BigDecimal = BigDecimal("0.20")) : CreditRule {
    init {
        require(warningThreshold >= BigDecimal.ZERO)
    }

    override val code = "RECENT_SPENDING_TREND"
    override val name = "Tendência recente de gastos"
    override val severity = RuleSeverity.WARNING

    override fun evaluate(context: CreditEvaluationContext): RuleResult {
        val first = context.monthlySpending.first()
        val last = context.monthlySpending.last()
        val growth = when {
            first.compareTo(BigDecimal.ZERO) == 0 && last > BigDecimal.ZERO -> BigDecimal.ONE
            first.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO
            else -> last.subtract(first).divide(first, MathContext.DECIMAL64)
        }
        val warning = growth > warningThreshold
        return RuleResult(
            code,
            name,
            severity,
            if (warning) RuleStatus.WARNING else RuleStatus.PASSED,
            if (warning) "Crescimento de gastos acima do limite de alerta" else "Tendência de gastos dentro do esperado",
            mapOf("growth" to growth.toPlainString(), "warningThreshold" to warningThreshold.toPlainString()),
        )
    }
}
