package io.github.brdoliveira.creditflow.infrastructure.outbox

import io.github.brdoliveira.creditflow.infrastructure.messaging.BrokerPublisher
import io.github.brdoliveira.creditflow.infrastructure.messaging.CreditEvaluationEventProducer
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxSchedulingConfiguration {
    @Bean
    @ConditionalOnBean(BrokerPublisher::class)
    fun creditEvaluationEventProducer(brokerPublisher: BrokerPublisher, objectMapper: ObjectMapper) =
        CreditEvaluationEventProducer(brokerPublisher, objectMapper)

    @Bean
    @ConditionalOnBean(CreditEvaluationEventProducer::class)
    fun outboxPublisher(store: OutboxStore, producer: CreditEvaluationEventProducer) = OutboxPublisher(store, producer)

    @Bean
    @ConditionalOnBean(OutboxPublisher::class)
    @ConditionalOnProperty(name = ["credit.outbox.scheduling-enabled"], havingValue = "true", matchIfMissing = true)
    fun scheduledOutboxPublisher(publisher: OutboxPublisher) = ScheduledOutboxPublisher(publisher)
}

class ScheduledOutboxPublisher(private val publisher: OutboxPublisher) {
    @Scheduled(fixedDelayString = "\${credit.outbox.poll-delay:PT1S}")
    fun publishPending() = publisher.publishPending()
}
