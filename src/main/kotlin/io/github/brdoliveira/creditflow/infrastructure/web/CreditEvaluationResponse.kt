package io.github.brdoliveira.creditflow.infrastructure.web

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class CreditEvaluationResponse(
    val evaluationId: UUID,
    val customerName: String,
    val maskedCpf: String,
    val decision: String,
    val approvedAmount: BigDecimal,
    val ruleSetVersion: String,
    val rules: List<RuleResponse>,
    val processedAt: OffsetDateTime,
    val processingTimeMs: Long,
    val correlationId: String
)

data class RuleResponse(
    val code: String,
    val name: String,
    val status: String,
    val reason: String
)

data class CreditEvaluationPageResponse(
    val items: List<CreditEvaluationResponse>,
    val total: Long,
    val page: Int,
    val size: Int,
    val sort: String
)
