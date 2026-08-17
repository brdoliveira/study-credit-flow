package io.github.brdoliveira.creditflow.application.port

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Storage boundary for an immutable, auditable credit decision.
 *
 * `maskedCpf` is deliberately the only CPF representation in this contract.
 */
interface CreditEvaluationRepository {
    fun save(snapshot: CreditEvaluationSnapshot): CreditEvaluationSnapshot

    fun findById(evaluationId: UUID): CreditEvaluationSnapshot?

    fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest): CreditEvaluationPage
}

data class CreditEvaluationSnapshot(
    val evaluationId: UUID,
    val maskedCpf: String,
    val decision: String,
    val approvedAmount: BigDecimal,
    val ruleVersion: String,
    val ruleResults: String,
    val evaluatedAt: Instant,
    val durationMillis: Long,
    val correlationId: String,
)

data class CreditEvaluationFilter(
    val decision: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

data class CreditEvaluationPageRequest(
    val page: Int = 0,
    val size: Int = 20,
    val sort: CreditEvaluationSort = CreditEvaluationSort.EVALUATED_AT_DESC,
) {
    init {
        require(page >= 0) { "page must not be negative" }
        require(size in 1..100) { "size must be between 1 and 100" }
    }
}

enum class CreditEvaluationSort {
    EVALUATED_AT_ASC,
    EVALUATED_AT_DESC,
    DECISION_ASC,
    DECISION_DESC,
    APPROVED_AMOUNT_ASC,
    APPROVED_AMOUNT_DESC,
}

data class CreditEvaluationPage(
    val items: List<CreditEvaluationSnapshot>,
    val total: Long,
    val page: Int,
    val size: Int,
    val sort: CreditEvaluationSort,
)
