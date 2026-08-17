package io.github.brdoliveira.creditflow.evaluation.application.report

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import java.time.Instant

/** Porta de aplicação para gerar o relatório a partir do modelo consolidado. */
fun interface CreditEvaluationReportGenerator {
    /** Gera os bytes do relatório para as avaliações recebidas. */
    fun generate(evaluations: List<CreditEvaluation>, generatedAt: Instant, decision: String?, from: Instant?, to: Instant?): ByteArray
}
