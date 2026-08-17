package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto

import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error.InvalidFilterException
import java.time.LocalDate
import java.time.ZoneOffset

/** Normaliza os filtros comuns aos endpoints de consulta e relatório. */
data class CreditEvaluationFilterCriteria(
    val decision: String?,
    val from: LocalDate?,
    val to: LocalDate?,
) {
    /** Valida os filtros e eventuais parâmetros adicionais aceitos pelo endpoint. */
    fun validate(parameterNames: Set<String>, additionalAllowed: Set<String> = emptySet()) {
        val allowed = FILTER_PARAMETERS + additionalAllowed
        val unknown = parameterNames - allowed
        valid(unknown.isEmpty()) { "Unknown filter: ${unknown.sorted().joinToString()}" }
        valid(decision == null || decision in DECISIONS) { "decision must be APPROVED or REJECTED" }
        valid(from == null || to == null || !from.isAfter(to)) { "from must not be after to" }
    }

    /** Converte datas e decisão HTTP para o filtro tipado da aplicação. */
    fun toFilter(): CreditEvaluationFilter = CreditEvaluationFilter(
        decision = decision?.let(CreditDecisionStatus::valueOf),
        from = from?.atStartOfDay(ZoneOffset.UTC)?.toInstant(),
        to = to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.minusNanos(1),
    )

    private fun valid(condition: Boolean, message: () -> String) {
        if (!condition) throw InvalidFilterException(message())
    }

    private companion object {
        val FILTER_PARAMETERS = setOf("decision", "from", "to")
        val DECISIONS = setOf("APPROVED", "REJECTED")
    }
}
