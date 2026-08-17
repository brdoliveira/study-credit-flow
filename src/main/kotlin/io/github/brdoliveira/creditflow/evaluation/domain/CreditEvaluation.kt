package io.github.brdoliveira.creditflow.evaluation.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Registro imutável produzido pelo caso de uso e persistido pela porta. */
data class CreditEvaluation(
    val evaluationId: UUID,
    val maskedCpf: String,
    val decision: CreditDecision,
    val approvedAmount: BigDecimal,
    val ruleSetVersion: String,
    val processedAt: Instant,
    val processingTimeMs: Long,
    val correlationId: String,
)
