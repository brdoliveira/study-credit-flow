package com.itau.credit.domain.rule

import com.itau.credit.domain.model.CreditEvaluationContext
import com.itau.credit.domain.model.RuleResult
import com.itau.credit.domain.model.RuleSeverity

interface CreditRule {
    val code: String
    val name: String
    val severity: RuleSeverity

    fun evaluate(context: CreditEvaluationContext): RuleResult
}
