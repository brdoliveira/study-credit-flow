package io.github.brdoliveira.creditflow.evaluation.domain

/**
 * Resultado tipado da execução de uma regra de crédito.
 *
 * @property code identificador estável da regra.
 * @property name nome legível da regra.
 * @property severity impacto da regra na decisão.
 * @property status resultado observado.
 * @property reason explicação exibida ao operador.
 * @property facts fatos auditáveis produzidos pela regra.
 */
data class RuleResult(
    val code: String,
    val name: String,
    val severity: RuleSeverity,
    val status: RuleStatus,
    val reason: String,
    val facts: Map<String, String> = emptyMap(),
) {
    /** Indica se a regra não bloqueou a avaliação. */
    val passed: Boolean get() = status != RuleStatus.FAILED
}
