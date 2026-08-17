package io.github.brdoliveira.creditflow.evaluation.domain.rule

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity

/** Contrato comum de uma regra explicável de crédito. */
interface CreditRule {
    val code: String
    val name: String
    val severity: RuleSeverity

    /** Avalia o contexto e produz um resultado auditável. */
    fun evaluate(context: CreditEvaluationContext): RuleResult
}
