package io.github.brdoliveira.creditflow.infrastructure.messaging

import io.github.brdoliveira.creditflow.application.event.CreditEvaluationCompleted
import tools.jackson.databind.ObjectMapper

/** Publica eventos de conclusão da avaliação no broker configurado. */
class CreditEvaluationEventProducer(
    private val broker: BrokerPublisher,
    private val objectMapper: ObjectMapper,
    private val topic: String = "credit.evaluation.completed.v1",
) {
    /** Serializa e publica o evento de avaliação concluída. */
    fun publish(event: CreditEvaluationCompleted) {
        broker.publish(topic, event.eventId.toString(), objectMapper.writeValueAsString(event))
    }
}
