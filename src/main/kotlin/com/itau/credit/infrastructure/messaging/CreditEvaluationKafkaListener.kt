package com.itau.credit.infrastructure.messaging

import com.itau.credit.application.event.CreditEvaluationCompleted
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.ObjectProvider
import org.springframework.kafka.annotation.KafkaListener
import tools.jackson.databind.ObjectMapper

class CreditEvaluationKafkaListener(
    objectMapper: ObjectMapper,
    processedEventStore: ProcessedEventStore,
    eventEffect: ObjectProvider<CreditEvaluationEventEffect>,
) {
    private val consumer = IdempotentCreditEvaluationConsumer(
        processedEventStore,
        eventEffect.getIfAvailable { CreditEvaluationEventEffect { } },
    )
    private val reader = objectMapper.readerFor(CreditEvaluationCompleted::class.java)

    @KafkaListener(
        topics = ["\${credit.messaging.completed-topic:credit.evaluation.completed.v1}"],
        groupId = "\${credit.messaging.consumer-group:credit-evaluation-effects-v1}",
    )
    fun onMessage(record: ConsumerRecord<String, String>) {
        consumer.consume(reader.readValue<CreditEvaluationCompleted>(record.value()))
    }

    internal fun onPayload(payload: String): ConsumptionResult =
        consumer.consume(reader.readValue<CreditEvaluationCompleted>(payload))
}
