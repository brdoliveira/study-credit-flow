package com.itau.credit.domain.rule

import com.itau.credit.domain.model.CreditDecision
import com.itau.credit.domain.model.CreditDecisionStatus
import com.itau.credit.domain.model.CreditEvaluationContext
import com.itau.credit.domain.model.RuleSeverity

class RuleEngine(private val rules: List<CreditRule>) {
    init {
        require(rules.isNotEmpty()) { "At least one credit rule must be registered" }
        require(rules.map(CreditRule::code).distinct().size == rules.size) { "Rule codes must be unique" }
    }

    fun evaluate(context: CreditEvaluationContext): CreditDecision {
        val results = rules.map { it.evaluate(context) }
        val rejected = results.any { it.severity == RuleSeverity.BLOCKING && !it.passed }
        return CreditDecision(
            status = if (rejected) CreditDecisionStatus.REJECTED else CreditDecisionStatus.APPROVED,
            ruleResults = results,
        )
    }
}
