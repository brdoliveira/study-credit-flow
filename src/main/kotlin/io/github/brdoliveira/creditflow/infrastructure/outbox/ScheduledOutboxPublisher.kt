package io.github.brdoliveira.creditflow.infrastructure.outbox

import org.springframework.scheduling.annotation.Scheduled

/** Aciona periodicamente a publicação dos eventos pendentes. */
class ScheduledOutboxPublisher(private val publisher: OutboxPublisher) {
    /** Publica os eventos pendentes conforme o intervalo configurado. */
    @Scheduled(fixedDelayString = "\${credit.outbox.poll-delay:PT1S}")
    fun publishPending() = publisher.publishPending()
}
