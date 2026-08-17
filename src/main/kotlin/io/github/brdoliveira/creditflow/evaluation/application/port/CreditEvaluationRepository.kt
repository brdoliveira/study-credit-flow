package io.github.brdoliveira.creditflow.evaluation.application.port

import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPage
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import java.util.UUID

/** Porta de persistência da avaliação consolidada. */
interface CreditEvaluationRepository {
    /** Persiste e devolve a avaliação imutável. */
    fun save(evaluation: CreditEvaluation): CreditEvaluation

    /** Consulta uma avaliação por identificador. */
    fun findById(evaluationId: UUID): CreditEvaluation?

    /** Lista avaliações com filtros, paginação e ordenação explícitos. */
    fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest): CreditEvaluationPage
}
