package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

import io.github.brdoliveira.creditflow.evaluation.application.event.CreditEvaluationCompleted
import io.github.brdoliveira.creditflow.platform.observability.CorrelationLogContext
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
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
        consume(reader.readValue(record.value()))
    }

    /** Consome diretamente um payload, permitindo validar a integração sem o broker. */
    internal fun onPayload(payload: String): ConsumptionResult =
        consume(reader.readValue(payload))

    private fun consume(event: CreditEvaluationCompleted): ConsumptionResult =
        CorrelationLogContext.withCorrelationId(event.correlationId) {
            val result = runCatching { consumer.consume(event) }.getOrElse { error ->
                logger.error(
                    "Kafka evaluation consumption failed: eventId={}, failureType={}",
                    event.eventId,
                    error.javaClass.simpleName,
                )
                throw error
            }
            when (result) {
                ConsumptionResult.PROCESSED -> logger.debug("Kafka evaluation event processed: eventId={}", event.eventId)
                ConsumptionResult.DUPLICATE_ACKNOWLEDGED -> logger.info("Kafka evaluation event duplicated: eventId={}", event.eventId)
            }
            result
        }

    private companion object {
        val logger = LoggerFactory.getLogger(CreditEvaluationKafkaListener::class.java)
    }
}
