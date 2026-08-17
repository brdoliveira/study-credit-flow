package io.github.brdoliveira.creditflow.application.evaluation

import java.math.BigDecimal

/** Input already validated by the delivery adapter. */
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
