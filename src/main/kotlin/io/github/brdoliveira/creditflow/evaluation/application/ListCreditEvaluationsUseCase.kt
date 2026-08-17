package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import java.time.Instant

/** Lista avaliações aplicando filtros e paginação na porta de persistência. */
class ListCreditEvaluationsUseCase(private val repository: CreditEvaluationRepository) {
    /** Retorna a página solicitada. */
    fun execute(filter: CreditEvaluationFilter = CreditEvaluationFilter(), page: CreditEvaluationPageRequest = CreditEvaluationPageRequest()): CreditEvaluationPage = repository.findPage(filter, page)

    /** Lista avaliações a partir dos parâmetros HTTP já normalizados. */
    fun execute(decision: String?, from: Instant?, to: Instant?, page: Int, size: Int): CreditEvaluationPage = execute(CreditEvaluationFilter(decision, from, to), CreditEvaluationPageRequest(page, size))
}
