package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

import java.util.UUID

/** Persiste o identificador do evento e seu efeito na mesma transação local. */
fun interface ProcessedEventStore {
    /** Executa o efeito apenas para um identificador ainda não processado. */
    fun processOnce(eventId: UUID, effect: () -> Unit): Boolean
}
