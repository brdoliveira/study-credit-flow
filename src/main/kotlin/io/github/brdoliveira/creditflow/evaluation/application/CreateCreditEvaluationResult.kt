package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation

/** Resultado tipado da criação antes da adaptação para HTTP. */
data class CreateCreditEvaluationResult(
    val evaluation: CreditEvaluation,
    val customerName: String,
)
