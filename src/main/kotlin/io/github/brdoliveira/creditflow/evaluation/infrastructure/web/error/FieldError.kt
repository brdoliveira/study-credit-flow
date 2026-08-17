package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error

/** Descreve um erro associado a um campo de entrada. */
data class FieldError(
    val field: String,
    val message: String,
)
