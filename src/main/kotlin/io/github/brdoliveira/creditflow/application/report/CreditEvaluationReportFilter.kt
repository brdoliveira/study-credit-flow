package io.github.brdoliveira.creditflow.application.report

import java.time.LocalDate

data class CreditEvaluationReportFilter(
    val decision: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    fun validate(parameterNames: Set<String> = emptySet()) {
        val unknown = parameterNames - ALLOWED_PARAMETERS
        require(unknown.isEmpty()) { "Unknown filter: ${unknown.sorted().joinToString()}" }
        require(decision == null || decision in VALID_DECISIONS) { "decision must be APPROVED or REJECTED" }
        require(from == null || to == null || !from.isAfter(to)) { "from must not be after to" }
    }

    companion object {
        private val ALLOWED_PARAMETERS = setOf("decision", "from", "to")
        private val VALID_DECISIONS = setOf("APPROVED", "REJECTED")
    }
}
