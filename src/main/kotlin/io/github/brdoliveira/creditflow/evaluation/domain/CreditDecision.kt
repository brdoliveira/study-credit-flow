package io.github.brdoliveira.creditflow.evaluation.domain

/**
 * Decisão consolidada pelo motor de regras.
 *
 * @property status resultado final da avaliação.
 * @property ruleResults resultados completos usados para explicar a decisão.
 */
data class CreditDecision(
    val status: CreditDecisionStatus,
    val ruleResults: List<RuleResult>,
)
