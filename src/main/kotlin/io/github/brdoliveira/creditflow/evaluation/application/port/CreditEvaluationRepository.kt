package io.github.brdoliveira.creditflow.evaluation.application.port

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import java.time.Instant
import java.util.UUID

/** Porta de persistência da avaliação consolidada. */
interface CreditEvaluationRepository {
    fun save(evaluation: CreditEvaluation): CreditEvaluation
    fun findById(evaluationId: UUID): CreditEvaluation?
    fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest): CreditEvaluationPage
}

/** Filtros disponíveis para consulta de avaliações persistidas. */
data class CreditEvaluationFilter(val decision: String? = null, val from: Instant? = null, val to: Instant? = null)

/** Paginação e ordenação da consulta. */
data class CreditEvaluationPageRequest(val page: Int = 0, val size: Int = 20) {
    init { require(page >= 0); require(size in 1..100) }
}

/** Página tipada de avaliações. */
data class CreditEvaluationPage(val items: List<CreditEvaluation>, val total: Long, val page: Int, val size: Int)
