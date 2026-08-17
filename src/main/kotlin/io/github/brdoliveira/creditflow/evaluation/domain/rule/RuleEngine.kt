package io.github.brdoliveira.creditflow.evaluation.domain.rule

import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecision
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity

/** Executa todas as regras e consolida a decisão final. */
class RuleEngine(private val rules: List<CreditRule>) {
    init {
        require(rules.isNotEmpty()) { "At least one credit rule must be registered" }
        require(rules.map(CreditRule::code).distinct().size == rules.size) { "Rule codes must be unique" }
    }

    /** Avalia todas as regras para preservar a explicabilidade da decisão. */
    fun evaluate(context: CreditEvaluationContext): CreditDecision {
        val results = rules.map { it.evaluate(context) }
        val rejected = results.any { it.severity == RuleSeverity.BLOCKING && !it.passed }
        return CreditDecision(
            status = if (rejected) CreditDecisionStatus.REJECTED else CreditDecisionStatus.APPROVED,
            ruleResults = results,
        )
    }
}
