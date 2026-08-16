package com.itau.credit.domain.model

enum class RuleSeverity {
    BLOCKING,
    WARNING,
}

enum class RuleStatus {
    PASSED,
    FAILED,
    WARNING,
}

data class RuleResult(
    val code: String,
    val name: String,
    val severity: RuleSeverity,
    val status: RuleStatus,
    val reason: String,
    val facts: Map<String, String> = emptyMap(),
) {
    val passed: Boolean = status != RuleStatus.FAILED
}
