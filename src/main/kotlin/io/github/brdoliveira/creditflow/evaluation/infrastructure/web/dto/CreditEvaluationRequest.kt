package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto

import jakarta.validation.constraints.*
import java.math.BigDecimal

/** Representa os dados enviados para uma nova avaliação de crédito. */
data class CreditEvaluationRequest(
    @field:NotBlank(message = "name must be provided") val name: String?,
    @field:NotBlank(message = "cpf must be provided") @field:Pattern(regexp = "\\d{11}", message = "cpf must contain 11 digits") val cpf: String?,
    @field:NotNull @field:Min(0) @field:Max(1000) val creditScore: Int?,
    @field:NotNull @field:DecimalMin("0.00") val currentInvoiceAmount: BigDecimal?,
    @field:NotNull @field:DecimalMin("0.01") val totalLimit: BigDecimal?,
    @field:NotNull @field:DecimalMin("0.00") val availableLimit: BigDecimal?,
    @field:NotNull @field:Min(0) val latePayments: Int?,
    @field:NotEmpty @field:Size(min = 3, max = 3, message = "monthlySpending must contain the latest three months") val monthlySpending: List<@DecimalMin("0.00") BigDecimal>?,
) {
    /** Verifica o dígito de controle do CPF informado. */
    @AssertTrue(message = "cpf is invalid")
    fun isCpfValid(): Boolean {
        val value = cpf ?: return true
        if (!value.matches(Regex("\\d{11}"))) return true
        if (value.all { it == value.first() }) return false
        val digits = value.map(Char::digitToInt)
        fun check(items: List<Int>, weight: Int) = (items.mapIndexed { i, d -> d * (weight - i) }.sum() % 11).let { if (it < 2) 0 else 11 - it }
        val first = check(digits.take(9), 10)
        return digits[9] == first && digits[10] == check(digits.take(9) + first, 11)
    }
}
