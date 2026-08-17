package com.itau.credit.application.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Public, versioned event contract. Deliberately contains no customer identifier.
 */
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
