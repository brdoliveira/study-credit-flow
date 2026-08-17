package io.github.brdoliveira.creditflow.evaluation.application.report

import io.github.brdoliveira.creditflow.evaluation.application.ListCreditEvaluationsUseCase
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import java.time.Clock
import java.time.Instant

/** Consulta todas as páginas e delega a renderização do relatório ao adaptador. */
class GenerateCreditEvaluationReportUseCase(
    private val list: ListCreditEvaluationsUseCase,
    private val generator: CreditEvaluationReportGenerator,
    private val clock: Clock = Clock.systemUTC(),
    private val maximumRows: Int = DEFAULT_MAXIMUM_ROWS,
) {
    init {
        require(maximumRows > 0) { "maximumRows must be positive" }
    }

    /** Gera um relatório completo, respeitando os filtros da consulta. */
    fun execute(
        decision: String? = null,
        from: Instant? = null,
        to: Instant? = null,
        generatedAt: Instant = clock.instant(),
    ): ByteArray = execute(CreditEvaluationFilter(decision, from, to), generatedAt)

    /** Gera um relatório a partir do filtro normalizado pelo adaptador de entrada. */
    fun execute(filter: CreditEvaluationFilter, generatedAt: Instant = clock.instant()): ByteArray {
        val snapshotFilter = filter.copy(to = filter.to?.coerceAtMost(generatedAt) ?: generatedAt)
        val values = mutableListOf<CreditEvaluation>()
        val firstPage = list.execute(snapshotFilter, CreditEvaluationPageRequest(page = 0, size = PAGE_SIZE))
        if (firstPage.total > maximumRows) {
            throw ReportRowLimitExceededException(firstPage.total, maximumRows)
        }
        values += firstPage.items
        val total = firstPage.total
        var page = 1
        while (values.size < total) {
            val result = list.execute(snapshotFilter, CreditEvaluationPageRequest(page, PAGE_SIZE))
            if (result.items.isEmpty()) break
            values += result.items
            page++
        }
        return generator.generate(values, generatedAt, filter.decision?.name, filter.from, filter.to)
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val DEFAULT_MAXIMUM_ROWS = 10_000
    }
}
