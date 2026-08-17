package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto

/** Representa uma página de avaliações devolvida pela API. */
data class CreditEvaluationPageResponse(val items: List<CreditEvaluationResponse>, val total: Long, val page: Int, val size: Int, val sort: String)
