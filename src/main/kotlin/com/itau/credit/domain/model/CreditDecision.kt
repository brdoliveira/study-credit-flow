package com.itau.credit.domain.model

enum class CreditDecisionStatus {
    APPROVED,
    REJECTED,
}

data class CreditDecision(
    val status: CreditDecisionStatus,
    val ruleResults: List<RuleResult>,
)
