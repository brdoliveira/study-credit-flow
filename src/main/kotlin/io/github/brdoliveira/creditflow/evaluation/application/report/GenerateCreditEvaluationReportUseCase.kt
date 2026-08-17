package io.github.brdoliveira.creditflow.evaluation.application.report

import io.github.brdoliveira.creditflow.evaluation.application.ListCreditEvaluationsUseCase
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationPageRequest
import java.time.Clock
import java.time.Instant

/** Consulta todas as páginas e delega a renderização do relatório ao adaptador. */
class GenerateCreditEvaluationReportUseCase(private val list: ListCreditEvaluationsUseCase, private val generator: CreditEvaluationReportGenerator, private val clock: Clock = Clock.systemUTC()) {
    /** Gera um relatório completo, respeitando os filtros da consulta. */
    fun execute(decision: String? = null, from: Instant? = null, to: Instant? = null): ByteArray {
        val filter = CreditEvaluationFilter(decision, from, to); val values = mutableListOf<io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation>(); var page = 0; var total: Long
        do { val result = list.execute(filter, CreditEvaluationPageRequest(page, 100)); values += result.items; total = result.total; page++ } while (values.size < total)
        return generator.generate(values, clock.instant(), decision, from, to)
    }
}
