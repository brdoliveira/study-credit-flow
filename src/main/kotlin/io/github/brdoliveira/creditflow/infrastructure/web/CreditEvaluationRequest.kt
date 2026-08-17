package io.github.brdoliveira.creditflow.infrastructure.web

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreditEvaluationRequest(
    @field:NotBlank(message = "name must be provided")
    val name: String?,
    @field:NotBlank(message = "cpf must be provided")
    @field:Pattern(regexp = "\\d{11}", message = "cpf must contain 11 digits")
    val cpf: String?,
    @field:NotNull @field:Min(0) @field:Max(1000)
    val creditScore: Int?,
    @field:NotNull @field:DecimalMin("0.00")
    val currentInvoiceAmount: BigDecimal?,
    @field:NotNull @field:DecimalMin("0.01")
    val totalLimit: BigDecimal?,
    @field:NotNull @field:DecimalMin("0.00")
    val availableLimit: BigDecimal?,
    @field:NotNull @field:Min(0)
    val latePayments: Int?,
    @field:NotEmpty @field:Size(min = 3, max = 3, message = "monthlySpending must contain the latest three months")
    val monthlySpending: List<@DecimalMin("0.00") BigDecimal>?
) {
    @AssertTrue(message = "cpf is invalid")
    fun isCpfValid(): Boolean = cpf == null || !cpf.matches(Regex("\\d{11}")) || isValidCpf(cpf)

    private fun isValidCpf(value: String): Boolean {
        if (value.all { it == value.first() }) return false
        val digits = value.map(Char::digitToInt)
        val first = checkDigit(digits.take(9), 10)
        val second = checkDigit(digits.take(9) + first, 11)
        return digits[9] == first && digits[10] == second
    }

    private fun checkDigit(digits: List<Int>, initialWeight: Int): Int {
        val remainder = digits.foldIndexed(0) { index, sum, digit -> sum + digit * (initialWeight - index) } % 11
        return if (remainder < 2) 0 else 11 - remainder
    }
}
