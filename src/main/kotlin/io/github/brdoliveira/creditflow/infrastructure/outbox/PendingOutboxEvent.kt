package io.github.brdoliveira.creditflow.infrastructure.outbox

import io.github.brdoliveira.creditflow.application.event.CreditEvaluationCompleted
import java.util.UUID

/** Evento pendente de publicação na outbox. */
data class PendingOutboxEvent(
    val eventId: UUID,
    val event: CreditEvaluationCompleted,
    val attempts: Int,
)
