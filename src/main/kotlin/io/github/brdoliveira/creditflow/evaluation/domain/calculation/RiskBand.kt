package io.github.brdoliveira.creditflow.evaluation.domain.calculation

import java.math.BigDecimal

/** Faixa de score associada a um fator de risco. */
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
