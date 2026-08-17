package io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox

import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.CreditEvaluationEventProducer
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.TransientBrokerException
import io.github.brdoliveira.creditflow.platform.observability.CorrelationLogContext
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Publica registros confirmados da outbox com retentativas exponenciais limitadas. */
class OutboxPublisher(
    private val outboxStore: OutboxStore,
    private val producer: CreditEvaluationEventProducer,
    private val clock: Clock = Clock.systemUTC(),
    private val retryBaseDelay: Duration = Duration.ofSeconds(1),
    private val maximumRetryDelay: Duration = Duration.ofMinutes(5),
    private val maximumAttempts: Int = 10,
) {
    init {
        require(maximumAttempts > 0) { "maximumAttempts must be positive" }
    }

    /** Publica até o limite informado de eventos pendentes. */
    @Suppress("TooGenericExceptionCaught")
    fun publishPending(limit: Int = 100) {
        require(limit > 0) { "limit must be positive" }
        val now = clock.instant()
        outboxStore.pending(now, limit).forEach { pending ->
            CorrelationLogContext.withCorrelationId(pending.event.correlationId) {
                try {
                    producer.publish(pending.event)
                    outboxStore.markPublished(pending.eventId, now)
                } catch (error: TransientBrokerException) {
                    handleTransientFailure(pending, now, error)
                } catch (error: Exception) {
                    val attempts = pending.attempts + 1
                    outboxStore.markFailed(
                        pending.eventId,
                        attempts,
                        now,
                        "Permanent publication failure (${error.javaClass.simpleName})",
                    )
                    logger.error(
                        "Outbox publication failed permanently: eventId={}, attempt={}, failureType={}",
                        pending.eventId,
                        attempts,
                        error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun handleTransientFailure(pending: PendingOutboxEvent, now: Instant, error: Exception) {
        val attempts = pending.attempts + 1
        if (attempts >= maximumAttempts) {
            outboxStore.markFailed(
                pending.eventId,
                attempts,
                now,
                "Retry limit reached (${error.javaClass.simpleName})",
            )
            logger.error(
                "Outbox retry limit reached: eventId={}, attempt={}, failureType={}",
                pending.eventId,
                attempts,
                error.javaClass.simpleName,
            )
            return
        }
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

    private fun backoff(attempts: Int): Duration {
        val multiplier = 1L shl (attempts - 1).coerceAtMost(20)
        val candidate = retryBaseDelay.multipliedBy(multiplier)
        return if (candidate > maximumRetryDelay) maximumRetryDelay else candidate
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OutboxPublisher::class.java)
    }
}
