package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller

import io.github.brdoliveira.creditflow.evaluation.application.*
import io.github.brdoliveira.creditflow.evaluation.application.port.*
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.*
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.mapper.CreditEvaluationWebMapper
import io.github.brdoliveira.creditflow.infrastructure.web.EvaluationNotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Expõe a criação, consulta e listagem de avaliações de crédito. */
@RestController
@RequestMapping("/api/v1/credit-evaluations")
class CreditEvaluationController(private val evaluate: EvaluateRevolvingCreditUseCase, private val find: FindCreditEvaluationUseCase, private val list: ListCreditEvaluationsUseCase, private val mapper: CreditEvaluationWebMapper) {
    /** Cria uma avaliação e devolve sua localização. */
    @PostMapping
    fun create(@Valid @RequestBody request: CreditEvaluationRequest, @RequestHeader("Idempotency-Key") idempotencyKey: String, @RequestHeader("X-Correlation-ID", required = false) correlationId: String?): ResponseEntity<CreditEvaluationResponse> {
        val value = evaluate.execute(EvaluateCreditCommand(request.name!!, request.cpf!!, request.creditScore!!, request.currentInvoiceAmount!!, request.totalLimit!!, request.availableLimit!!, request.latePayments!!, request.monthlySpending!!, correlationId ?: UUID.randomUUID().toString()))
        val response = mapper.toResponse(value, request.name)
        return ResponseEntity.created(URI.create("/api/v1/credit-evaluations/${response.evaluationId}")).header("Idempotency-Replayed", "false").body(response)
    }
    /** Consulta uma avaliação pelo identificador. */
    @GetMapping("/{evaluationId}")
    fun findById(@PathVariable evaluationId: UUID): CreditEvaluationResponse = find.execute(evaluationId)?.let(mapper::toResponse) ?: throw EvaluationNotFoundException(evaluationId)
    /** Lista avaliações com paginação e filtros básicos. */
    @GetMapping
    fun list(@RequestParam(required = false) decision: String?, @RequestParam(required = false) from: LocalDate?, @RequestParam(required = false) to: LocalDate?, @RequestParam(defaultValue = "0") @Min(0) page: Int, @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int, @RequestParam(defaultValue = "processedAt") sort: String, @RequestParam(defaultValue = "DESC") direction: String): CreditEvaluationPageResponse {
        val result = list.execute(CreditEvaluationFilter(decision, from?.atStartOfDay(ZoneOffset.UTC)?.toInstant(), to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()), CreditEvaluationPageRequest(page, size))
        return CreditEvaluationPageResponse(result.items.map(mapper::toResponse), result.total, result.page, result.size, "$sort,${direction.uppercase()}")
    }
}
