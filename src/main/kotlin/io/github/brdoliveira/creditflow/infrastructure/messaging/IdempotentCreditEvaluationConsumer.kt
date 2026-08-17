package io.github.brdoliveira.creditflow.infrastructure.messaging

import io.github.brdoliveira.creditflow.application.event.CreditEvaluationCompleted
import java.util.UUID

fun interface CreditEvaluationEventEffect {
    fun handle(event: CreditEvaluationCompleted)
}

/**
 * Implementations execute the insert of eventId and the effect in the same local
 * transaction. They return false for an existing eventId, which is an acknowledged
 * duplicate delivery rather than an error.
 */
fun interface ProcessedEventStore {
    fun processOnce(eventId: UUID, effect: () -> Unit): Boolean
}

enum class ConsumptionResult { PROCESSED, DUPLICATE_ACKNOWLEDGED }

class IdempotentCreditEvaluationConsumer(
    private val processedEventStore: ProcessedEventStore,
    private val effect: CreditEvaluationEventEffect,
) {
    fun consume(event: CreditEvaluationCompleted): ConsumptionResult =
        if (processedEventStore.processOnce(event.eventId) { effect.handle(event) }) {
            ConsumptionResult.PROCESSED
        } else {
            ConsumptionResult.DUPLICATE_ACKNOWLEDGED
        }
}
