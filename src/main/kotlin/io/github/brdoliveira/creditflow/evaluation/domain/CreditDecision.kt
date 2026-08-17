package io.github.brdoliveira.creditflow.evaluation.domain

/** Decisão consolidada pelo motor de regras e seus resultados auditáveis. */
data class CreditDecision(
    val status: Status,
    val ruleResults: List<RuleResult>,
) {
    /** Resultado final da avaliação de crédito. */
    enum class Status { APPROVED, REJECTED }
}
