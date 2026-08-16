package com.itau.credit.infrastructure.web

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.LocalDate
import java.util.UUID

@RestController
@Validated
@RequestMapping("/api/v1/credit-evaluations")
class CreditEvaluationController(
    private val service: CreditEvaluationApiService
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreditEvaluationRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestHeader(value = "X-Correlation-ID", required = false) correlationId: String?
    ): ResponseEntity<CreditEvaluationResponse> {
        val response = service.evaluate(request, idempotencyKey, correlationId.orNewCorrelationId())
        return ResponseEntity.created(URI.create("/api/v1/credit-evaluations/${response.evaluationId}")).body(response)
    }

    @GetMapping("/{evaluationId}")
    fun findById(
        @PathVariable evaluationId: UUID,
        @RequestHeader(value = "X-Correlation-ID", required = false) correlationId: String?
    ): CreditEvaluationResponse = service.findById(evaluationId, correlationId.orNewCorrelationId())
        ?: throw EvaluationNotFoundException(evaluationId)

    @GetMapping
    fun list(
        @RequestParam(required = false) decision: String?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(defaultValue = "processedAt") sort: String,
        @RequestParam(defaultValue = "DESC") direction: String,
        @RequestParam params: Map<String, String>,
        @RequestHeader(value = "X-Correlation-ID", required = false) correlationId: String?
    ): CreditEvaluationPageResponse {
        val criteria = CreditEvaluationSearchCriteria(decision, from, to, page, size, sort, direction)
        criteria.validate(params.keys)
        return service.list(criteria, correlationId.orNewCorrelationId())
    }
}

interface CreditEvaluationApiService {
    fun evaluate(request: CreditEvaluationRequest, idempotencyKey: String, correlationId: String): CreditEvaluationResponse
    fun findById(evaluationId: UUID, correlationId: String): CreditEvaluationResponse?
    fun list(criteria: CreditEvaluationSearchCriteria, correlationId: String): CreditEvaluationPageResponse
}

data class CreditEvaluationSearchCriteria(
    val decision: String?,
    val from: LocalDate?,
    val to: LocalDate?,
    val page: Int,
    val size: Int,
    val sort: String,
    val direction: String
) {
    fun validate(parameterNames: Set<String>) {
        val allowed = setOf("decision", "from", "to", "page", "size", "sort", "direction")
        val unknown = parameterNames - allowed
        valid(unknown.isEmpty()) { "Unknown filter: ${unknown.sorted().joinToString()}" }
        valid(decision == null || decision in setOf("APPROVED", "REJECTED")) { "decision must be APPROVED or REJECTED" }
        valid(from == null || to == null || !from.isAfter(to)) { "from must not be after to" }
        valid(sort in setOf("processedAt", "decision", "approvedAmount")) { "Unsupported sort field: $sort" }
        valid(direction.uppercase() in setOf("ASC", "DESC")) { "direction must be ASC or DESC" }
    }

    private fun valid(condition: Boolean, message: () -> String) {
        if (!condition) throw InvalidFilterException(message())
    }
}

class EvaluationNotFoundException(evaluationId: UUID) : RuntimeException("Credit evaluation $evaluationId was not found")
class InvalidFilterException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private fun String?.orNewCorrelationId(): String = this?.takeIf { it.isNotBlank() && it.length <= 128 } ?: UUID.randomUUID().toString()
