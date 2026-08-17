package io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox

import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.CreditEvaluationEventProducer
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.TransientBrokerException
import java.time.Clock
import java.time.Duration

/** Publica registros confirmados da outbox com retentativas exponenciais limitadas. */
class OutboxPublisher(
    private val outboxStore: OutboxStore,
    private val producer: CreditEvaluationEventProducer,
    private val clock: Clock = Clock.systemUTC(),
    private val retryBaseDelay: Duration = Duration.ofSeconds(1),
    private val maximumRetryDelay: Duration = Duration.ofMinutes(5),
) {
    /** Publica até o limite informado de eventos pendentes. */
    fun publishPending(limit: Int = 100) {
        require(limit > 0) { "limit must be positive" }
        val now = clock.instant()
        outboxStore.pending(now, limit).forEach { pending ->
            try {
                producer.publish(pending.event)
                outboxStore.markPublished(pending.eventId, now)
            } catch (error: TransientBrokerException) {
                val attempts = pending.attempts + 1
                outboxStore.reschedule(
                    pending.eventId,
                    attempts,
                    now.plus(backoff(attempts)),
                    "Broker publication failed (${error.javaClass.simpleName})",
                )
            }
        }
    }

    private fun backoff(attempts: Int): Duration {
        val multiplier = 1L shl (attempts - 1).coerceAtMost(20)
        val candidate = retryBaseDelay.multipliedBy(multiplier)
        return if (candidate > maximumRetryDelay) maximumRetryDelay else candidate
    }
}
