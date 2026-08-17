package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import java.util.UUID

/** Consulta uma avaliação por seu identificador. */
class FindCreditEvaluationUseCase(private val repository: CreditEvaluationRepository) {
    /** Retorna a avaliação ou nulo quando ela não existe. */
    fun execute(evaluationId: UUID): CreditEvaluation? = repository.findById(evaluationId)
}
