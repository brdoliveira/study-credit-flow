package io.github.brdoliveira.creditflow.evaluation.application.port

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationResult

/** Porta para reservar uma requisição e reutilizar seu resultado persistido. */
interface IdempotencyRepository {
    /** Executa uma criação uma única vez para a chave e o conteúdo informados. */
    fun execute(
        key: String?,
        requestBody: String,
        operation: () -> CreateCreditEvaluationResult,
    ): IdempotencyExecution
}
