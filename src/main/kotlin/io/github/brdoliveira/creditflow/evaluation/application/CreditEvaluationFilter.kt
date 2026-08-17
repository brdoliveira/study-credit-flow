package io.github.brdoliveira.creditflow.evaluation.application

import java.time.Instant

/** Filtros disponíveis para consultar avaliações persistidas. */
data class CreditEvaluationFilter(
    val decision: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)
