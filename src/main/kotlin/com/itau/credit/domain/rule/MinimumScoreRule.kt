package com.itau.credit.domain.rule

import com.itau.credit.domain.model.CreditEvaluationContext
import com.itau.credit.domain.model.RuleResult
import com.itau.credit.domain.model.RuleSeverity
import com.itau.credit.domain.model.RuleStatus

class MinimumScoreRule(private val minimumScore: Int = 650) : CreditRule {
    init {
        require(minimumScore in 0..1000)
    }

    override val code = "MINIMUM_SCORE"
    override val name = "Score mínimo"
    override val severity = RuleSeverity.BLOCKING

    override fun evaluate(context: CreditEvaluationContext): RuleResult {
        val passed = context.creditScore >= minimumScore
        return RuleResult(
            code,
            name,
            severity,
            if (passed) RuleStatus.PASSED else RuleStatus.FAILED,
            if (passed) "Score atende ao mínimo configurado" else "Score abaixo do mínimo configurado",
            mapOf("score" to context.creditScore.toString(), "minimumScore" to minimumScore.toString()),
        )
    }
}
