package io.github.brdoliveira.creditflow.infrastructure.messaging

import io.github.brdoliveira.creditflow.application.event.CreditEvaluationCompleted

/** Representa o efeito produzido pelo consumo de uma avaliação concluída. */
fun interface CreditEvaluationEventEffect {
    /** Executa o efeito associado ao evento recebido. */
    fun handle(event: CreditEvaluationCompleted)
}
