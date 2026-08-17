package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.mapper

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationResult
import io.github.brdoliveira.creditflow.evaluation.application.EvaluateCreditCommand
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.*
import java.time.ZoneOffset

/** Converte modelos da aplicação em representações próprias da web. */
class CreditEvaluationWebMapper {
    /** Converte e valida o DTO já aceito pelo Bean Validation. */
    fun toCommand(request: CreditEvaluationRequest, correlationId: String) = EvaluateCreditCommand(
        customerName = requireNotNull(request.name),
        cpf = requireNotNull(request.cpf),
        creditScore = requireNotNull(request.creditScore),
        currentInvoiceAmount = requireNotNull(request.currentInvoiceAmount),
        totalLimit = requireNotNull(request.totalLimit),
        availableLimit = requireNotNull(request.availableLimit),
        latePayments = requireNotNull(request.latePayments),
        monthlySpending = requireNotNull(request.monthlySpending),
        correlationId = correlationId,
    )

    /** Converte uma avaliação para o payload HTTP. */
    fun toResponse(value: CreditEvaluation, customerName: String = "Cliente") = CreditEvaluationResponse(value.evaluationId, customerName, value.maskedCpf, value.decision.name, value.approvedAmount, value.ruleSetVersion, value.ruleResults.map { RuleResponse(it.code, it.name, it.status.name, it.reason) }, value.processedAt.atOffset(ZoneOffset.UTC), value.processingTimeMs, value.correlationId)

    /** Converte o resultado da criação preservando o nome informado. */
    fun toResponse(result: CreateCreditEvaluationResult) = toResponse(result.evaluation, result.customerName)
}
