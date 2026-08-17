package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation

/** Página de avaliações devolvida por uma consulta. */
data class CreditEvaluationPage(
    val items: List<CreditEvaluation>,
    val total: Long,
    val page: Int,
    val size: Int,
    val sort: CreditEvaluationSort,
)
