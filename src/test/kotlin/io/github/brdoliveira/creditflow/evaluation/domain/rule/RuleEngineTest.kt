package io.github.brdoliveira.creditflow.evaluation.domain.rule

import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus
import io.github.brdoliveira.creditflow.evaluation.domain.rule.AvailableLimitRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.CreditRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.LimitCommitmentRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.MaxLatePaymentsRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.MinimumScoreRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RecentSpendingTrendRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RuleEngine
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleEngineTest {
    @Test
    // @spec:AC-004
    fun `AC-004 executes every registered rule even after a blocking failure`() {
        val visited = mutableListOf<String>()
        val rules = listOf(recordingRule("FAIL", false, visited), recordingRule("PASS", true, visited))
        val decision = RuleEngine(rules).evaluate(context())
        assertEquals(listOf("FAIL", "PASS"), visited)
        assertEquals(2, decision.ruleResults.size)
    }

    @Test
    // @spec:AC-005
    fun `AC-005 score below 650 rejects the evaluation`() {
        val decision = RuleEngine(listOf(MinimumScoreRule())).evaluate(context(creditScore = 649))
        assertEquals(CreditDecisionStatus.REJECTED, decision.status)
        assertEquals(RuleStatus.FAILED, decision.ruleResults.single().status)
    }

    @Test
    // @spec:AC-006
    fun `AC-006 more than two late payments rejects the evaluation`() {
        val decision = RuleEngine(listOf(MaxLatePaymentsRule())).evaluate(context(latePayments = 3))
        assertEquals(CreditDecisionStatus.REJECTED, decision.status)
    }

    @Test
    // @spec:AC-007
    fun `AC-007 zero available limit rejects the evaluation`() {
        val decision = RuleEngine(listOf(AvailableLimitRule())).evaluate(context(availableLimit = BigDecimal.ZERO))
        assertEquals(CreditDecisionStatus.REJECTED, decision.status)
    }

    @Test
    // @spec:AC-008
    fun `AC-008 commitment above 80 percent rejects the evaluation`() {
        val decision = RuleEngine(listOf(LimitCommitmentRule())).evaluate(
            context(currentInvoiceAmount = BigDecimal("4000.01"), totalLimit = BigDecimal("5000.00")),
        )
        assertEquals(CreditDecisionStatus.REJECTED, decision.status)
    }

    @Test
    // @spec:AC-009
    fun `AC-009 spending growth above 20 percent warns without rejecting`() {
        val decision = RuleEngine(listOf(RecentSpendingTrendRule())).evaluate(
            context(monthlySpending = listOf(BigDecimal("1000"), BigDecimal("1100"), BigDecimal("1300"))),
        )
        assertEquals(CreditDecisionStatus.APPROVED, decision.status)
        assertEquals(RuleStatus.WARNING, decision.ruleResults.single().status)
    }

    @Test
    // @spec:AC-010
    fun `AC-010 a new rule participates without changing the engine`() {
        val custom = recordingRule("CUSTOM_RULE", true, mutableListOf())
        val decision = RuleEngine(listOf(MinimumScoreRule(), custom)).evaluate(context())
        assertEquals(listOf("MINIMUM_SCORE", "CUSTOM_RULE"), decision.ruleResults.map(RuleResult::code))
    }

    @Test
    // @spec:AC-011
    fun `AC-011 equal inputs and configuration produce equal decisions`() {
        val engine = RuleEngine(defaultRules())
        assertEquals(engine.evaluate(context()), engine.evaluate(context()))
    }

    private fun defaultRules() = listOf(
        MinimumScoreRule(),
        MaxLatePaymentsRule(),
        AvailableLimitRule(),
        LimitCommitmentRule(),
        RecentSpendingTrendRule(),
    )

    private fun recordingRule(code: String, passed: Boolean, visited: MutableList<String>) = object : CreditRule {
        override val code = code
        override val name = code
        override val severity = RuleSeverity.BLOCKING
        override fun evaluate(context: CreditEvaluationContext): RuleResult {
            visited += code
            return RuleResult(code, name, severity, if (passed) RuleStatus.PASSED else RuleStatus.FAILED, code)
        }
    }

    private fun context(
        creditScore: Int = 750,
        currentInvoiceAmount: BigDecimal = BigDecimal("1000.00"),
        totalLimit: BigDecimal = BigDecimal("5000.00"),
        availableLimit: BigDecimal = BigDecimal("4000.00"),
        latePayments: Int = 0,
        monthlySpending: List<BigDecimal> = listOf(BigDecimal("1000"), BigDecimal("1100"), BigDecimal("1150")),
    ) = CreditEvaluationContext(
        creditScore, currentInvoiceAmount, totalLimit, availableLimit, latePayments, monthlySpending,
    )
}
