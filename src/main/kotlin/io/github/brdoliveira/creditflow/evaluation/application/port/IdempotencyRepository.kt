package io.github.brdoliveira.creditflow.evaluation.application.port

/** Porta para reservar uma requisição e reutilizar seu resultado persistido. */
interface IdempotencyRepository {
    fun execute(key: String?, requestBody: String, operation: () -> String): IdempotencyExecution
}

/** Resultado da execução, indicando se veio de uma repetição idempotente. */
data class IdempotencyExecution(val responseBody: String, val replayed: Boolean)
