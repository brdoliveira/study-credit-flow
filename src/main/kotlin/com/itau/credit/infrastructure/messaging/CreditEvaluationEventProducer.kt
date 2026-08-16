package com.itau.credit.infrastructure.messaging

import com.itau.credit.application.event.CreditEvaluationCompleted
import tools.jackson.databind.ObjectMapper

fun interface BrokerPublisher {
    /** Throws [TransientBrokerException] when the broker can be retried later. */
    fun publish(topic: String, key: String, payload: String)
}

class TransientBrokerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class CreditEvaluationEventProducer(
    private val broker: BrokerPublisher,
    private val objectMapper: ObjectMapper,
    private val topic: String = "credit.evaluation.completed.v1",
) {
    fun publish(event: CreditEvaluationCompleted) {
        broker.publish(topic, event.eventId.toString(), objectMapper.writeValueAsString(event))
    }
}
