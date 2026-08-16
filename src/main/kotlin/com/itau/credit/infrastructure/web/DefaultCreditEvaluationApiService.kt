package com.itau.credit.infrastructure.web

import com.itau.credit.application.evaluation.CreditEvaluationResult
import com.itau.credit.application.evaluation.EvaluateCreditCommand
import com.itau.credit.application.evaluation.EvaluateRevolvingCreditUseCase
import com.itau.credit.application.port.CreditEvaluationFilter
import com.itau.credit.application.port.CreditEvaluationPageRequest
import com.itau.credit.application.port.CreditEvaluationRepository
import com.itau.credit.application.port.CreditEvaluationSnapshot
import com.itau.credit.application.port.CreditEvaluationSort
import com.itau.credit.application.port.IdempotencyRepository
import com.itau.credit.infrastructure.observability.ObservedCreditEvaluationService
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.ZoneOffset
import java.util.UUID

@Service
class DefaultCreditEvaluationApiService(
    private val useCase: EvaluateRevolvingCreditUseCase,
    private val repository: CreditEvaluationRepository,
    private val idempotencyRepository: IdempotencyRepository,
    private val objectMapper: ObjectMapper,
    private val observer: ObservedCreditEvaluationService,
) : CreditEvaluationApiService {
    override fun evaluate(
        request: CreditEvaluationRequest,
        idempotencyKey: String,
        correlationId: String,
    ): CreditEvaluationResponse = evaluateWithOutcome(request, idempotencyKey, correlationId).response

    override fun evaluateWithOutcome(
        request: CreditEvaluationRequest,
        idempotencyKey: String,
        correlationId: String,
    ): IdempotentCreditEvaluationResponse = observer.observe {
        val requestJson = objectMapper.writeValueAsString(request)
        val execution = idempotencyRepository.executeWithOutcome(idempotencyKey, requestJson) {
            val result = useCase.execute(request.toCommand(correlationId))
            objectMapper.writeValueAsString(result.toResponse(request.name!!))
        }
        IdempotentCreditEvaluationResponse(
            objectMapper.readValue(execution.responseBody, CreditEvaluationResponse::class.java),
            execution.replayed,
        )
    }

    override fun findById(evaluationId: UUID, correlationId: String): CreditEvaluationResponse? =
        repository.findById(evaluationId)?.toResponse()

    override fun list(criteria: CreditEvaluationSearchCriteria, correlationId: String): CreditEvaluationPageResponse {
        val page = repository.findPage(
            CreditEvaluationFilter(
                criteria.decision,
                criteria.from?.atStartOfDay(ZoneOffset.UTC)?.toInstant(),
                criteria.to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.minusNanos(1),
            ),
            CreditEvaluationPageRequest(criteria.page, criteria.size, criteria.toSort()),
        )
        return CreditEvaluationPageResponse(
            page.items.map { it.toResponse() },
            page.total,
            page.page,
            page.size,
            "${criteria.sort},${criteria.direction.uppercase()}",
        )
    }

    private fun CreditEvaluationRequest.toCommand(correlationId: String) = EvaluateCreditCommand(
        name!!,
        cpf!!,
        creditScore!!,
        currentInvoiceAmount!!,
        totalLimit!!,
        availableLimit!!,
        latePayments!!,
        monthlySpending!!,
        correlationId,
    )

    private fun CreditEvaluationResult.toResponse(customerName: String) = CreditEvaluationResponse(
        evaluationId,
        customerName,
        maskedCpf,
        decision.name,
        approvedAmount,
        ruleSetVersion,
        executedRules.map { RuleResponse(it.code, it.name, it.status.name, it.reason) },
        processedAt.atOffset(ZoneOffset.UTC),
        processingTimeMs,
        correlationId,
    )

    private fun CreditEvaluationSnapshot.toResponse(): CreditEvaluationResponse {
        val rules = objectMapper.readValue(
            ruleResults,
            objectMapper.typeFactory.constructCollectionType(List::class.java, StoredRule::class.java),
        ) as List<StoredRule>
        return CreditEvaluationResponse(
            evaluationId,
            "Cliente",
            maskedCpf,
            decision,
            approvedAmount,
            ruleVersion,
            rules.map { RuleResponse(it.code, it.name, it.status, it.reason) },
            evaluatedAt.atOffset(ZoneOffset.UTC),
            durationMillis,
            correlationId,
        )
    }

    private fun CreditEvaluationSearchCriteria.toSort(): CreditEvaluationSort = when (sort to direction.uppercase()) {
        "processedAt" to "ASC" -> CreditEvaluationSort.EVALUATED_AT_ASC
        "processedAt" to "DESC" -> CreditEvaluationSort.EVALUATED_AT_DESC
        "decision" to "ASC" -> CreditEvaluationSort.DECISION_ASC
        "decision" to "DESC" -> CreditEvaluationSort.DECISION_DESC
        "approvedAmount" to "ASC" -> CreditEvaluationSort.APPROVED_AMOUNT_ASC
        else -> CreditEvaluationSort.APPROVED_AMOUNT_DESC
    }

    private data class StoredRule(
        val code: String,
        val name: String,
        val severity: String,
        val status: String,
        val reason: String,
    )
}
