package io.github.brdoliveira.creditflow.evaluation.application

import java.math.BigDecimal

/** Dados de entrada do fluxo de avaliação, já validados pelo adaptador. */
data class EvaluateCreditCommand(
    val customerName: String,
    val cpf: String,
    val creditScore: Int,
    val currentInvoiceAmount: BigDecimal,
    val totalLimit: BigDecimal,
    val availableLimit: BigDecimal,
    val latePayments: Int,
    val monthlySpending: List<BigDecimal>,
    val correlationId: String,
)
