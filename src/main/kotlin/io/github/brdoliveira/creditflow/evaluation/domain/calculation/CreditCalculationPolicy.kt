package io.github.brdoliveira.creditflow.evaluation.domain.calculation

import java.math.BigDecimal
import java.math.RoundingMode

/** Parâmetros imutáveis usados pelo cálculo do limite aprovado. */
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

    /** Retorna o fator correspondente ao score informado. */
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
