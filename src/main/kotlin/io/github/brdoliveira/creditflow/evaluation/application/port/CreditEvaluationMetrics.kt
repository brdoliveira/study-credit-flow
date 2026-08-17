package io.github.brdoliveira.creditflow.evaluation.application.port

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import java.time.Duration

/** Porta de métricas com cardinalidade limitada para novas avaliações. */
fun interface CreditEvaluationMetrics {
    /** Registra uma avaliação concluída e sua duração ponta a ponta. */
    fun record(evaluation: CreditEvaluation, duration: Duration)
}
