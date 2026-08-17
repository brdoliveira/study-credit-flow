package io.github.brdoliveira.creditflow.evaluation.domain.calculation

import java.math.BigDecimal

/** Calcula o valor de crédito disponível conforme risco e elegibilidade. */
interface CreditLimitCalculator {
    /** Calcula o valor respeitando a elegibilidade consolidada. */
    fun calculate(availableLimit: BigDecimal, creditScore: Int, eligible: Boolean): BigDecimal

    /** Calcula o valor para uma avaliação previamente considerada elegível. */
    fun calculate(availableLimit: BigDecimal, creditScore: Int): BigDecimal =
        calculate(availableLimit, creditScore, eligible = true)
}
