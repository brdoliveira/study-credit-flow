package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import java.time.Instant

/** Filtros disponíveis para consultar avaliações persistidas. */
data class CreditEvaluationFilter(
    val decision: CreditDecisionStatus? = null,
    val from: Instant? = null,
    val to: Instant? = null,
) {
    /** Mantém compatibilidade para chamadores legados que ainda passam o valor HTTP. */
    constructor(decision: String?, from: Instant?, to: Instant?) : this(
        decision?.let(CreditDecisionStatus::valueOf),
        from,
        to,
    )
}
