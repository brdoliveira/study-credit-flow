package io.github.brdoliveira.creditflow.infrastructure.messaging

import io.github.brdoliveira.creditflow.application.event.CreditEvaluationCompleted
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.ObjectProvider
import org.springframework.kafka.annotation.KafkaListener
import tools.jackson.databind.ObjectMapper

/** Recebe eventos Kafka e os encaminha ao consumidor idempotente. */
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
    /** Consome uma mensagem entregue pelo tópico de avaliações concluídas. */
    fun onMessage(record: ConsumerRecord<String, String>) {
        consumer.consume(reader.readValue<CreditEvaluationCompleted>(record.value()))
    }

    /** Consome diretamente um payload, permitindo validar a integração sem o broker. */
    internal fun onPayload(payload: String): ConsumptionResult =
        consumer.consume(reader.readValue<CreditEvaluationCompleted>(payload))
}
