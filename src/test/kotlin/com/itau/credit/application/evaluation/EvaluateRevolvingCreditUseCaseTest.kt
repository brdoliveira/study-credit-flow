package com.itau.credit.application.evaluation

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EvaluateRevolvingCreditUseCaseTest {
    private val instant = Instant.parse("2026-08-15T12:00:00Z")
    private val fixedId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    // @spec:AC-001
    fun `AC-001 creates a single evaluation for a valid command`() {
        val store = RecordingStore()
        val result = useCase(passingRules(), store).execute(validCommand())

        assertEquals(fixedId, result.evaluationId)
        assertEquals(1, store.snapshots.size)
        assertEquals(CreditDecision.APPROVED, result.decision)
    }

    @Test
    // @spec:AC-003
    fun `AC-003 returns a rejected decision instead of a technical error`() {
        val store = RecordingStore()
        var calculatorCalled = false
        val useCase = useCase(
            RuleEvaluation("rules-v1", listOf(blockingFailure())),
            store,
            RevolvingCreditCalculator { calculatorCalled = true; BigDecimal("999.99") },
        )

        val result = useCase.execute(validCommand())

        assertEquals(CreditDecision.REJECTED, result.decision)
        assertEquals(BigDecimal.ZERO, result.approvedAmount)
        assertEquals(false, calculatorCalled)
    }

    @Test
    // @spec:AC-015
    fun `AC-015 returns all decision traceability fields with a masked CPF`() {
        val result = useCase(passingRules(), RecordingStore()).execute(validCommand())

        assertEquals(fixedId, result.evaluationId)
        assertEquals("***.***.***-09", result.maskedCpf)
        assertEquals(CreditDecision.APPROVED, result.decision)
        assertEquals(BigDecimal("700.00"), result.approvedAmount)
        assertEquals("rules-v1", result.ruleSetVersion)
        assertEquals(1, result.executedRules.size)
        assertEquals(instant, result.processedAt)
        assertEquals(0, result.processingTimeMs)
        assertEquals("corr-123", result.correlationId)
    }

    @Test
    // @spec:AC-016
    fun `AC-016 persists an immutable photograph of the completed decision`() {
        val store = RecordingStore()
        val rules = mutableListOf(passingRule())
        val result = useCase(RuleEvaluation("rules-v1", rules), store).execute(validCommand())
        rules += blockingFailure()

        val persisted = store.snapshots.single()
        assertEquals(result.evaluationId, persisted.evaluationId)
        assertEquals(result.decision, persisted.decision)
        assertEquals(result.approvedAmount, persisted.approvedAmount)
        assertEquals("rules-v1", persisted.ruleSetVersion)
        assertEquals(1, persisted.executedRules.size)
        assertNotEquals("12345678909", persisted.maskedCpf)
    }

    private fun useCase(
        evaluation: RuleEvaluation,
        store: RecordingStore,
        calculator: RevolvingCreditCalculator = RevolvingCreditCalculator { BigDecimal("700.00") },
    ) = EvaluateRevolvingCreditUseCase(
        ruleEvaluator = CreditRuleEvaluator { evaluation },
        creditCalculator = calculator,
        snapshotStore = store,
        clock = Clock.fixed(instant, ZoneOffset.UTC),
        idGenerator = { fixedId },
    )

    private fun passingRules() = RuleEvaluation("rules-v1", listOf(passingRule()))

    private fun passingRule() = ExecutedRule(
        code = "MINIMUM_SCORE",
        name = "Minimum score",
        severity = RuleSeverity.BLOCKING,
        status = RuleStatus.PASSED,
        reason = "Score satisfies the configured threshold",
    )

    private fun blockingFailure() = ExecutedRule(
        code = "MINIMUM_SCORE",
        name = "Minimum score",
        severity = RuleSeverity.BLOCKING,
        status = RuleStatus.FAILED,
        reason = "Score is below the configured threshold",
    )

    private fun validCommand() = EvaluateCreditCommand(
        customerName = "Maria Silva",
        cpf = "12345678909",
        creditScore = 800,
        currentInvoiceAmount = BigDecimal("1000.00"),
        totalLimit = BigDecimal("5000.00"),
        availableLimit = BigDecimal("4000.00"),
        latePayments = 0,
        monthlySpending = listOf(BigDecimal("1000.00"), BigDecimal("1100.00"), BigDecimal("1200.00")),
        correlationId = "corr-123",
    )

    private class RecordingStore : CreditEvaluationSnapshotStore {
        val snapshots = mutableListOf<CreditEvaluationSnapshot>()

        override fun save(snapshot: CreditEvaluationSnapshot): CreditEvaluationSnapshot {
            snapshots += snapshot
            return snapshot
        }
    }
}
