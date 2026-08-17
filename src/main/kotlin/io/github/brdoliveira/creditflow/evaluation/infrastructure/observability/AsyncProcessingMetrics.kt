package io.github.brdoliveira.creditflow.evaluation.infrastructure.observability

/** Registra resultados assíncronos usando somente tags de cardinalidade limitada. */
interface AsyncProcessingMetrics {
    /** Registra o resultado de uma tentativa de publicação da outbox. */
    fun recordOutbox(outcome: OutboxOutcome)

    /** Registra o resultado do consumo de um evento Kafka. */
    fun recordKafka(outcome: KafkaOutcome)

    companion object {
        /** Implementação neutra para testes unitários e composição fora do Spring. */
        val NONE = object : AsyncProcessingMetrics {
            override fun recordOutbox(outcome: OutboxOutcome) = Unit
            override fun recordKafka(outcome: KafkaOutcome) = Unit
        }
    }
}
