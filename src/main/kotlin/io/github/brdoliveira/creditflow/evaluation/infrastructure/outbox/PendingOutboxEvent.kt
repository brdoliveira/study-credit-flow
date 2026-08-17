package io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox

import io.github.brdoliveira.creditflow.evaluation.application.event.CreditEvaluationCompleted
import java.util.UUID

/** Evento pendente de publicação na outbox. */
data class PendingOutboxEvent(
    val eventId: UUID,
    val event: CreditEvaluationCompleted,
    val attempts: Int,
)
