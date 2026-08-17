package io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox

import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.BrokerPublisher
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.CreditEvaluationEventProducer
import io.github.brdoliveira.creditflow.evaluation.infrastructure.observability.AsyncProcessingMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import tools.jackson.databind.ObjectMapper

/** Configura a publicação periódica dos eventos da outbox. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxSchedulingConfiguration {
    /** Cria o produtor de eventos quando há um publicador de broker disponível. */
    @Bean
    fun creditEvaluationEventProducer(brokerPublisher: BrokerPublisher, objectMapper: ObjectMapper) =
        CreditEvaluationEventProducer(brokerPublisher, objectMapper)

    /** Cria o publicador da outbox quando o produtor está disponível. */
    @Bean
    fun outboxPublisher(
        store: OutboxStore,
        producer: CreditEvaluationEventProducer,
        metrics: AsyncProcessingMetrics,
        @Value("\${credit.outbox.maximum-attempts:10}") maximumAttempts: Int,
    ) = OutboxPublisher(store, producer, maximumAttempts = maximumAttempts, metrics = metrics)

    /** Cria o agendador da outbox quando a funcionalidade está habilitada. */
    @Bean
    @ConditionalOnProperty(name = ["credit.outbox.scheduling-enabled"], havingValue = "true", matchIfMissing = true)
    fun scheduledOutboxPublisher(publisher: OutboxPublisher) = ScheduledOutboxPublisher(publisher)
}
