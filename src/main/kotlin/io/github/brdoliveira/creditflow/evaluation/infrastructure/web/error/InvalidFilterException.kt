package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error

/** Indica que os filtros recebidos não respeitam o contrato HTTP. */
class InvalidFilterException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
