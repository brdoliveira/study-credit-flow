package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error

import java.time.OffsetDateTime

/** Representa o erro padronizado devolvido pela API. */
data class ApiError(val status: Int, val code: String, val message: String, val correlationId: String, val path: String, val fieldErrors: List<FieldError> = emptyList(), val timestamp: OffsetDateTime = OffsetDateTime.now())

/** Descreve um erro associado a um campo de entrada. */
data class FieldError(val field: String, val message: String)
