package io.github.brdoliveira.creditflow.evaluation.domain

/** Resultado tipado da execução de uma regra de crédito. */
data class RuleResult(
    val code: String,
    val name: String,
    val severity: Severity,
    val status: Status,
    val reason: String,
    val facts: Map<String, String> = emptyMap(),
) {
    /** Indica se a regra não bloqueou a avaliação. */
    val passed: Boolean get() = status != Status.FAILED

    /** Severidade atribuída à regra. */
    enum class Severity { BLOCKING, WARNING }

    /** Estado observado após a execução da regra. */
    enum class Status { PASSED, FAILED, WARNING }
}
