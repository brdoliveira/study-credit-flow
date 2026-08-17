package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

/** Resultado do consumo idempotente de um evento. */
enum class ConsumptionResult {
    /** Evento processado pela primeira vez. */
    PROCESSED,

    /** Entrega duplicada reconhecida sem repetir o efeito. */
    DUPLICATE_ACKNOWLEDGED,
}
