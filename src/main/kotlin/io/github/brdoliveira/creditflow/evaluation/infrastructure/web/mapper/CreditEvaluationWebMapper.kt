package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.mapper

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.*
import java.time.ZoneOffset

/** Converte modelos da aplicação em representações próprias da web. */
class CreditEvaluationWebMapper {
    /** Converte uma avaliação para o payload HTTP. */
    fun toResponse(value: CreditEvaluation, customerName: String = "Cliente") = CreditEvaluationResponse(value.evaluationId, customerName, value.maskedCpf, value.decision.status.name, value.approvedAmount, value.ruleSetVersion, value.decision.ruleResults.map { RuleResponse(it.code, it.name, it.status.name, it.reason) }, value.processedAt.atOffset(ZoneOffset.UTC), value.processingTimeMs, value.correlationId)
}
