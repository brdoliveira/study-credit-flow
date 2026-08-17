package io.github.brdoliveira.creditflow.evaluation.application.port

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationResult

/** Resultado de uma criação protegida por idempotência. */
data class IdempotencyExecution(
    val result: CreateCreditEvaluationResult,
    val replayed: Boolean,
)
