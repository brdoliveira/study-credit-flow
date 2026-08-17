package io.github.brdoliveira.creditflow.evaluation.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

/** Implementação Micrometer que registra previamente todas as séries permitidas. */
class MicrometerAsyncProcessingMetrics(registry: MeterRegistry) : AsyncProcessingMetrics {
    private val outboxCounters = OutboxOutcome.entries.associateWith { outcome ->
        Counter.builder("credit.outbox.events")
            .description("Outbox publication attempts by outcome")
            .tag("outcome", outcome.tag)
            .register(registry)
    }
    private val kafkaCounters = KafkaOutcome.entries.associateWith { outcome ->
        Counter.builder("credit.kafka.events")
            .description("Kafka consumption attempts by outcome")
            .tag("outcome", outcome.tag)
            .register(registry)
    }

    /** Incrementa o contador correspondente ao resultado da outbox. */
    override fun recordOutbox(outcome: OutboxOutcome) = outboxCounters.getValue(outcome).increment()

    /** Incrementa o contador correspondente ao resultado do Kafka. */
    override fun recordKafka(outcome: KafkaOutcome) = kafkaCounters.getValue(outcome).increment()
}
