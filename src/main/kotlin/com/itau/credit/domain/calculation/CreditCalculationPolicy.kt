package com.itau.credit.domain.calculation

import java.math.BigDecimal
import java.math.RoundingMode

data class CreditCalculationPolicy(
    val availableLimitPercentage: BigDecimal = BigDecimal("0.70"),
    val maximumAmount: BigDecimal = BigDecimal("5000.00"),
    val scale: Int = 2,
    val roundingMode: RoundingMode = RoundingMode.HALF_EVEN,
    val riskBands: List<RiskBand> = DEFAULT_RISK_BANDS,
) {
    init {
        require(availableLimitPercentage > BigDecimal.ZERO && availableLimitPercentage <= BigDecimal.ONE)
        require(maximumAmount >= BigDecimal.ZERO)
        require(scale >= 0)
        require(riskBands.isNotEmpty())
    }

    fun riskFactor(score: Int): BigDecimal =
        riskBands.firstOrNull { score in it.minimumScore..it.maximumScore }?.factor
            ?: BigDecimal.ZERO

    companion object {
        val DEFAULT_RISK_BANDS = listOf(
            RiskBand(650, 699, BigDecimal("0.50")),
            RiskBand(700, 749, BigDecimal("0.75")),
            RiskBand(750, 1000, BigDecimal("1.00")),
        )
    }
}

data class RiskBand(
    val minimumScore: Int,
    val maximumScore: Int,
    val factor: BigDecimal,
) {
    init {
        require(minimumScore <= maximumScore)
        require(factor >= BigDecimal.ZERO && factor <= BigDecimal.ONE)
    }
}
