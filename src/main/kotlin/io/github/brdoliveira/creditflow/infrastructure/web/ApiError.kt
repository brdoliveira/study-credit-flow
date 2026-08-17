package io.github.brdoliveira.creditflow.infrastructure.web

import java.time.OffsetDateTime

data class ApiError(
    val status: Int,
    val code: String,
    val message: String,
    val correlationId: String,
    val path: String,
    val fieldErrors: List<ApiFieldError> = emptyList(),
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)

data class ApiFieldError(
    val field: String,
    val message: String
)
