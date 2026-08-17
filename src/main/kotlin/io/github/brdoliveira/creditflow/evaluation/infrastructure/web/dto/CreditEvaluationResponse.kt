package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/** Expõe o resultado de uma avaliação no contrato HTTP. */
data class CreditEvaluationResponse(val evaluationId: UUID, val customerName: String, val maskedCpf: String, val decision: String, val approvedAmount: BigDecimal, val ruleSetVersion: String, val rules: List<RuleResponse>, val processedAt: OffsetDateTime, val processingTimeMs: Long, val correlationId: String)

/** Expõe o resultado de uma regra no contrato HTTP. */
data class RuleResponse(val code: String, val name: String, val status: String, val reason: String)
