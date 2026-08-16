package com.itau.credit.domain.model

import java.math.BigDecimal

data class CreditEvaluationContext(
    val customerName: String,
    val cpf: String,
    val creditScore: Int,
    val currentInvoiceAmount: BigDecimal,
    val totalLimit: BigDecimal,
    val availableLimit: BigDecimal,
    val latePayments: Int,
    val monthlySpending: List<BigDecimal>,
) {
    init {
        require(customerName.isNotBlank()) { "Customer name is required" }
        require(creditScore in 0..1000) { "Credit score must be between 0 and 1000" }
        require(currentInvoiceAmount >= BigDecimal.ZERO) { "Current invoice must not be negative" }
        require(totalLimit > BigDecimal.ZERO) { "Total limit must be positive" }
        require(availableLimit >= BigDecimal.ZERO) { "Available limit must not be negative" }
        require(availableLimit <= totalLimit) { "Available limit must not exceed total limit" }
        require(latePayments >= 0) { "Late payments must not be negative" }
        require(monthlySpending.size == 3) { "Exactly three monthly spending values are required" }
        require(monthlySpending.all { it >= BigDecimal.ZERO }) { "Monthly spending must not be negative" }
    }
}
