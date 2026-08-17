package io.github.brdoliveira.creditflow.evaluation.infrastructure.observability

/** Resultados possíveis do consumo de um evento Kafka. */
enum class KafkaOutcome(val tag: String) {
    PROCESSED("processed"),
    DUPLICATE("duplicate"),
    FAILED("failed"),
}
