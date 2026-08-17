package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto

/** Expõe o resultado de uma regra no contrato HTTP. */
data class RuleResponse(
    val code: String,
    val name: String,
    val status: String,
    val reason: String,
)
