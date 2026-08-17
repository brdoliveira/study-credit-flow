package io.github.brdoliveira.creditflow.infrastructure.outbox

import java.time.Instant
import java.util.UUID

/** Porta de persistência dos eventos da outbox. */
interface OutboxStore {
    /** Obtém eventos disponíveis para publicação. */
    fun pending(now: Instant, limit: Int): List<PendingOutboxEvent>

    /** Marca um evento como publicado. */
    fun markPublished(eventId: UUID, publishedAt: Instant)

    /** Agenda uma nova tentativa de publicação. */
    fun reschedule(eventId: UUID, attempts: Int, nextAttemptAt: Instant, reason: String)
}
