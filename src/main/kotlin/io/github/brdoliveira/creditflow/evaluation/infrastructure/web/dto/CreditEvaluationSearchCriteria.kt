package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto

import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationSort
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error.InvalidFilterException
import java.time.LocalDate

/** Filtros e paginação aceitos pelo endpoint de listagem. */
data class CreditEvaluationSearchCriteria(
    val decision: String?,
    val from: LocalDate?,
    val to: LocalDate?,
    val page: Int,
    val size: Int,
    val sort: String,
    val direction: String,
) {
    /** Valida campos, intervalo, ordenação e parâmetros desconhecidos. */
    fun validate(parameterNames: Set<String>) {
        filters().validate(parameterNames, PAGINATION_PARAMETERS)
        valid(sort in setOf("processedAt", "decision", "approvedAmount")) {
            "Unsupported sort field: $sort"
        }
        valid(direction.uppercase() in setOf("ASC", "DESC")) { "direction must be ASC or DESC" }
    }

    /** Converte a ordenação HTTP para o contrato da porta. */
    fun toSort(): CreditEvaluationSort = when (sort to direction.uppercase()) {
        "processedAt" to "ASC" -> CreditEvaluationSort.EVALUATED_AT_ASC
        "processedAt" to "DESC" -> CreditEvaluationSort.EVALUATED_AT_DESC
        "decision" to "ASC" -> CreditEvaluationSort.DECISION_ASC
        "decision" to "DESC" -> CreditEvaluationSort.DECISION_DESC
        "approvedAmount" to "ASC" -> CreditEvaluationSort.APPROVED_AMOUNT_ASC
        else -> CreditEvaluationSort.APPROVED_AMOUNT_DESC
    }

    /** Converte a decisão HTTP validada para o estado do domínio. */
    fun toDecision(): CreditDecisionStatus? = decision?.let(CreditDecisionStatus::valueOf)

    /** Converte os filtros HTTP para o contrato tipado da aplicação. */
    fun toFilter(): CreditEvaluationFilter = filters().toFilter()

    private fun filters() = CreditEvaluationFilterCriteria(decision, from, to)

    private fun valid(condition: Boolean, message: () -> String) {
        if (!condition) throw InvalidFilterException(message())
    }

    private companion object {
        val PAGINATION_PARAMETERS = setOf("page", "size", "sort", "direction")
    }
}
