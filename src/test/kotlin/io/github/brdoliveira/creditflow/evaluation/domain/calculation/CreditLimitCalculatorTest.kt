package io.github.brdoliveira.creditflow.evaluation.domain.calculation

import io.github.brdoliveira.creditflow.evaluation.domain.calculation.ConfigurableCreditLimitCalculator
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class CreditLimitCalculatorTest {
    private val calculator = ConfigurableCreditLimitCalculator()

    @Test
    // @spec:AC-012
    fun `AC-012 applies percentage risk and configured cap without exceeding available limit`() {
        assertEquals(BigDecimal("1750.00"), calculator.calculate(BigDecimal("5000.00"), 650))
        assertEquals(BigDecimal("5000.00"), calculator.calculate(BigDecimal("10000.00"), 800))
        assertEquals(BigDecimal("0.35"), calculator.calculate(BigDecimal("0.50"), 800))
    }

    @Test
    // @spec:AC-013
    fun `AC-013 produces BRL scale using half even rounding`() {
        assertEquals(BigDecimal("0.86"), calculator.calculate(BigDecimal("1.23"), 800))
        assertEquals(BigDecimal("0.88"), calculator.calculate(BigDecimal("1.25"), 800))
    }

    @Test
    // @spec:AC-014
    fun `AC-014 rejected evaluation always receives zero`() {
        assertEquals(BigDecimal("0.00"), calculator.calculate(BigDecimal("10000.00"), 900, eligible = false))
    }
}
