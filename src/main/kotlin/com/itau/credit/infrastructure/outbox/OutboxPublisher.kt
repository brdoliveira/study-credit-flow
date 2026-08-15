package com.itau.credit.infrastructure.outbox

import com.itau.credit.application.event.CreditEvaluationCompleted
import com.itau.credit.infrastructure.messaging.CreditEvaluationEventProducer
import com.itau.credit.infrastructure.messaging.TransientBrokerException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PendingOutboxEvent(
    val eventId: UUID,
    val event: CreditEvaluationCompleted,
    val attempts: Int,
)

interface OutboxStore {
    fun pending(now: Instant, limit: Int): List<PendingOutboxEvent>
    fun markPublished(eventId: UUID, publishedAt: Instant)
    fun reschedule(eventId: UUID, attempts: Int, nextAttemptAt: Instant, reason: String)
}

/**
 * Publishes only committed outbox records. A transient error never drops the event:
 * it remains pending and gets a bounded exponential retry delay.
 */
class OutboxPublisher(
    private val outboxStore: OutboxStore,
    private val producer: CreditEvaluationEventProducer,
    private val clock: Clock = Clock.systemUTC(),
    private val retryBaseDelay: Duration = Duration.ofSeconds(1),
    private val maximumRetryDelay: Duration = Duration.ofMinutes(5),
) {
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
                    error.message ?: error.javaClass.simpleName,
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
