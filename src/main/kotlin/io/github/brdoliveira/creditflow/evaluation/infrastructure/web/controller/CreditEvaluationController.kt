package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.FindCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.ListCreditEvaluationsUseCase
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.CreditEvaluationPageResponse
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.CreditEvaluationRequest
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.CreditEvaluationResponse
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.CreditEvaluationSearchCriteria
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error.EvaluationNotFoundException
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.mapper.CreditEvaluationWebMapper
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
import java.time.ZoneOffset
import java.util.UUID

/** Expõe criação, consulta e listagem de avaliações de crédito. */
@RestController
@Validated
@RequestMapping("/api/v1/credit-evaluations")
class CreditEvaluationController(
    private val createEvaluation: CreateCreditEvaluationUseCase,
    private val findEvaluation: FindCreditEvaluationUseCase,
    private val listEvaluations: ListCreditEvaluationsUseCase,
    private val mapper: CreditEvaluationWebMapper,
) {
    /** Cria uma avaliação ou devolve o replay idempotente existente. */
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreditEvaluationRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestHeader(value = "X-Correlation-ID", required = false) correlationId: String?,
    ): ResponseEntity<CreditEvaluationResponse> {
        val outcome = createEvaluation.execute(
            mapper.toCommand(request, correlationId.orNewCorrelationId()),
            idempotencyKey,
        )
        val response = mapper.toResponse(outcome.result)
        return if (outcome.replayed) {
            ResponseEntity.ok().header("Idempotency-Replayed", "true").body(response)
        } else {
            ResponseEntity.created(URI.create("/api/v1/credit-evaluations/${response.evaluationId}"))
                .header("Idempotency-Replayed", "false")
                .body(response)
        }
    }

    /** Consulta uma avaliação por identificador. */
    @GetMapping("/{evaluationId}")
    fun findById(
        @PathVariable evaluationId: UUID,
    ): CreditEvaluationResponse = findEvaluation.execute(evaluationId)?.let(mapper::toResponse)
        ?: throw EvaluationNotFoundException(evaluationId)

    /** Lista avaliações com os filtros e a ordenação validados. */
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
    ): CreditEvaluationPageResponse {
        val criteria = CreditEvaluationSearchCriteria(decision, from, to, page, size, sort, direction)
        criteria.validate(params.keys)
        val result = listEvaluations.execute(
            CreditEvaluationFilter(
                criteria.toDecision(),
                from?.atStartOfDay(ZoneOffset.UTC)?.toInstant(),
                to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.minusNanos(1),
            ),
            CreditEvaluationPageRequest(page, size, criteria.toSort()),
        )
        return CreditEvaluationPageResponse(
            result.items.map(mapper::toResponse),
            result.total,
            result.page,
            result.size,
            "$sort,${direction.uppercase()}",
        )
    }
}

private fun String?.orNewCorrelationId(): String =
    this?.takeIf { it.isNotBlank() && it.length <= 128 } ?: UUID.randomUUID().toString()
