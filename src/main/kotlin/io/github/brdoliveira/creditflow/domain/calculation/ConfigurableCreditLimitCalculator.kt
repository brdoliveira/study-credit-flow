package io.github.brdoliveira.creditflow.domain.calculation

import java.math.BigDecimal

class ConfigurableCreditLimitCalculator(
    private val policy: CreditCalculationPolicy = CreditCalculationPolicy(),
) : CreditLimitCalculator {
    override fun calculate(availableLimit: BigDecimal, creditScore: Int, eligible: Boolean): BigDecimal {
        require(availableLimit >= BigDecimal.ZERO) { "Available limit must not be negative" }
        require(creditScore in 0..1000) { "Credit score must be between 0 and 1000" }

        if (!eligible) return BigDecimal.ZERO.setScale(policy.scale, policy.roundingMode)

        val riskAdjustedAmount = availableLimit
            .multiply(policy.availableLimitPercentage)
            .multiply(policy.riskFactor(creditScore))
        return riskAdjustedAmount
            .min(policy.maximumAmount)
            .min(availableLimit)
            .setScale(policy.scale, policy.roundingMode)
    }
}
