package io.github.brdoliveira.creditflow.platform.config

import io.github.brdoliveira.creditflow.platform.health.DependencyReadinessIndicator
import io.github.brdoliveira.creditflow.platform.health.KafkaReadinessProbe
import io.github.brdoliveira.creditflow.platform.health.RequiredDependencyProbe
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.BrokerPublisher
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.CreditEvaluationEventEffect
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.CreditEvaluationKafkaListener
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.KafkaBrokerPublisher
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.PostgresProcessedEventStore
import io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging.ProcessedEventStore
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.OutboxSchedulingConfiguration
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.OutboxStore
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.PostgresOutboxStore
import io.github.brdoliveira.creditflow.evaluation.infrastructure.observability.AsyncProcessingMetrics
import io.github.brdoliveira.creditflow.evaluation.infrastructure.observability.OutboxBacklogMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import javax.sql.DataSource

/** Compõe adaptadores de mensageria, outbox e prontidão das dependências. */
@Configuration(proxyBeanMethods = false)
@Import(OutboxSchedulingConfiguration::class)
class IntegrationConfiguration {
    /** Fornece o armazenamento PostgreSQL da outbox. */
    @Bean
    fun outboxStore(jdbcTemplate: JdbcTemplate, objectMapper: ObjectMapper): OutboxStore =
        PostgresOutboxStore(jdbcTemplate, objectMapper)

    /** Fornece o publicador síncrono para o broker Kafka. */
    @Bean
    fun brokerPublisher(kafkaTemplate: KafkaTemplate<String, String>): BrokerPublisher =
        KafkaBrokerPublisher(kafkaTemplate)

    /** Fornece o armazenamento de eventos já processados. */
    @Bean
    fun processedEventStore(
        jdbcTemplate: JdbcTemplate,
        transactions: TransactionTemplate,
    ): ProcessedEventStore = PostgresProcessedEventStore(jdbcTemplate, transactions)

    /** Compõe o listener Kafka idempotente. */
    @Bean
    fun creditEvaluationKafkaListener(
        objectMapper: ObjectMapper,
        processedEventStore: ProcessedEventStore,
        eventEffect: ObjectProvider<CreditEvaluationEventEffect>,
        metrics: AsyncProcessingMetrics,
    ) = CreditEvaluationKafkaListener(objectMapper, processedEventStore, eventEffect, metrics)

    /** Mantém gauges de backlog e falhas terminais da outbox. */
    @Bean
    @ConditionalOnProperty(name = ["credit.outbox.scheduling-enabled"], havingValue = "true", matchIfMissing = true)
    fun outboxBacklogMetrics(registry: MeterRegistry, jdbcTemplate: JdbcTemplate) =
        OutboxBacklogMetrics(registry, jdbcTemplate)

    /** Verifica PostgreSQL e Kafka sem misturá-los à liveness do processo. */
    @Bean("dependencyReadiness")
    fun dependencyReadiness(
        dataSource: DataSource,
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
    ): HealthIndicator = DependencyReadinessIndicator(
        mapOf(
            "postgres" to RequiredDependencyProbe { dataSource.connection.use { it.isValid(2) } },
            "kafka" to KafkaReadinessProbe(bootstrapServers),
        ),
    )
}
