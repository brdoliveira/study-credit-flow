package io.github.brdoliveira.creditflow.evaluation.domain

/** Estados possíveis após a execução de uma regra. */
enum class RuleStatus {
    PASSED,
    FAILED,
    WARNING,
}
