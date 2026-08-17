package io.github.brdoliveira.creditflow.evaluation.application

/** Resultado da criação com indicação explícita de replay. */
data class IdempotentCreditEvaluationResult(
    val result: CreateCreditEvaluationResult,
    val replayed: Boolean,
)
