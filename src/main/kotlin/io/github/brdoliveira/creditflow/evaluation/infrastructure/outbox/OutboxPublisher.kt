package io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox

import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.CreditEvaluationEventProducer
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.TransientBrokerException
import io.github.brdoliveira.creditflow.platform.observability.CorrelationLogContext
import org.slf4j.LoggerFactory
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
            CorrelationLogContext.withCorrelationId(pending.event.correlationId) {
                try {
                    producer.publish(pending.event)
                    outboxStore.markPublished(pending.eventId, now)
                } catch (error: TransientBrokerException) {
                    val attempts = pending.attempts + 1
                    val nextAttemptAt = now.plus(backoff(attempts))
                    outboxStore.reschedule(
                        pending.eventId,
                        attempts,
                        nextAttemptAt,
                        "Broker publication failed (${error.javaClass.simpleName})",
                    )
                    logger.warn(
                        "Outbox publication rescheduled: eventId={}, attempt={}, nextAttemptAt={}, failureType={}",
                        pending.eventId,
                        attempts,
                        nextAttemptAt,
                        error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun backoff(attempts: Int): Duration {
        val multiplier = 1L shl (attempts - 1).coerceAtMost(20)
        val candidate = retryBaseDelay.multipliedBy(multiplier)
        return if (candidate > maximumRetryDelay) maximumRetryDelay else candidate
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OutboxPublisher::class.java)
    }
}
