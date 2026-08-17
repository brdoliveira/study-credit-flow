package io.github.brdoliveira.creditflow.evaluation.infrastructure.observability

/** Resultados possíveis da publicação de um evento da outbox. */
enum class OutboxOutcome(val tag: String) {
    PUBLISHED("published"),
    RETRY("retry"),
    FAILED("failed"),
}
