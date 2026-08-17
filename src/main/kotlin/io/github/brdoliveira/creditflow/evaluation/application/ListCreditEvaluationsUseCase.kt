package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationPage
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository

/** Lista avaliações aplicando filtros e paginação na porta de persistência. */
class ListCreditEvaluationsUseCase(private val repository: CreditEvaluationRepository) {
    /** Retorna a página solicitada. */
    fun execute(filter: CreditEvaluationFilter = CreditEvaluationFilter(), page: CreditEvaluationPageRequest = CreditEvaluationPageRequest()): CreditEvaluationPage = repository.findPage(filter, page)
}
