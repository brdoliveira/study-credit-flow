package io.github.brdoliveira.creditflow.domain.rule

import io.github.brdoliveira.creditflow.domain.model.CreditEvaluationContext
import io.github.brdoliveira.creditflow.domain.model.RuleResult
import io.github.brdoliveira.creditflow.domain.model.RuleSeverity

interface CreditRule {
    val code: String
    val name: String
    val severity: RuleSeverity

    fun evaluate(context: CreditEvaluationContext): RuleResult
}
