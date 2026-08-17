package io.github.brdoliveira.creditflow.infrastructure.outbox

import io.github.brdoliveira.creditflow.infrastructure.messaging.BrokerPublisher
import io.github.brdoliveira.creditflow.infrastructure.messaging.CreditEvaluationEventProducer
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
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
    @ConditionalOnBean(BrokerPublisher::class)
    fun creditEvaluationEventProducer(brokerPublisher: BrokerPublisher, objectMapper: ObjectMapper) =
        CreditEvaluationEventProducer(brokerPublisher, objectMapper)

    /** Cria o publicador da outbox quando o produtor está disponível. */
    @Bean
    @ConditionalOnBean(CreditEvaluationEventProducer::class)
    fun outboxPublisher(store: OutboxStore, producer: CreditEvaluationEventProducer) = OutboxPublisher(store, producer)

    /** Cria o agendador da outbox quando a funcionalidade está habilitada. */
    @Bean
    @ConditionalOnBean(OutboxPublisher::class)
    @ConditionalOnProperty(name = ["credit.outbox.scheduling-enabled"], havingValue = "true", matchIfMissing = true)
    fun scheduledOutboxPublisher(publisher: OutboxPublisher) = ScheduledOutboxPublisher(publisher)
}
