package com.itau.credit.domain.calculation

import java.math.BigDecimal

interface CreditLimitCalculator {
    fun calculate(availableLimit: BigDecimal, creditScore: Int, eligible: Boolean): BigDecimal

    fun calculate(availableLimit: BigDecimal, creditScore: Int): BigDecimal =
        calculate(availableLimit, creditScore, eligible = true)
}
