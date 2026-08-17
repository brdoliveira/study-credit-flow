package io.github.brdoliveira.creditflow.application.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Contrato versionado do evento, sem qualquer identificador direto do cliente. */
data class CreditEvaluationCompleted(
    val eventId: UUID,
    val eventVersion: Int = VERSION,
    val evaluationId: UUID,
    val decision: String,
    val approvedAmount: BigDecimal,
    val ruleVersion: String,
    val evaluatedAt: Instant,
    val correlationId: String,
) {
    init {
        require(eventVersion == VERSION) { "Unsupported CreditEvaluationCompleted version: $eventVersion" }
    }

    companion object {
        const val TYPE = "CreditEvaluationCompleted"
        const val VERSION = 1
    }
}
