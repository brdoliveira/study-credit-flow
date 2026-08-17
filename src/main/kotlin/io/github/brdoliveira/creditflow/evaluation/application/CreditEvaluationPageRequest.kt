package io.github.brdoliveira.creditflow.evaluation.application

/** Parâmetros de paginação e ordenação de uma consulta. */
data class CreditEvaluationPageRequest(
    val page: Int = 0,
    val size: Int = 20,
    val sort: CreditEvaluationSort = CreditEvaluationSort.EVALUATED_AT_DESC,
) {
    init {
        require(page >= 0) { "page must not be negative" }
        require(size in 1..100) { "size must be between 1 and 100" }
    }
}
