package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

import io.github.brdoliveira.creditflow.evaluation.application.event.CreditEvaluationCompleted

/** Consome eventos de avaliação com garantia de idempotência. */
class IdempotentCreditEvaluationConsumer(
    private val processedEventStore: ProcessedEventStore,
    private val effect: CreditEvaluationEventEffect,
) {
    /** Processa o evento uma única vez e confirma entregas duplicadas. */
    fun consume(event: CreditEvaluationCompleted): ConsumptionResult =
        if (processedEventStore.processOnce(event.eventId) { effect.handle(event) }) {
            ConsumptionResult.PROCESSED
        } else {
            ConsumptionResult.DUPLICATE_ACKNOWLEDGED
        }
}
