package io.github.brdoliveira.creditflow.domain.model

enum class CreditDecisionStatus {
    APPROVED,
    REJECTED,
}

data class CreditDecision(
    val status: CreditDecisionStatus,
    val ruleResults: List<RuleResult>,
)
