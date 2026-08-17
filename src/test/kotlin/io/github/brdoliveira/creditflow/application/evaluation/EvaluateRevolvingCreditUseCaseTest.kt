package io.github.brdoliveira.creditflow.application.evaluation

import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPage
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.application.EvaluateCreditCommand
import io.github.brdoliveira.creditflow.evaluation.application.EvaluateRevolvingCreditUseCase
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus
import io.github.brdoliveira.creditflow.evaluation.domain.calculation.CreditLimitCalculator
import io.github.brdoliveira.creditflow.evaluation.domain.rule.CreditRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RuleEngine
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
        val repository = RecordingRepository()
        val result = useCase(passingRule(), repository).execute(validCommand())

        assertEquals(fixedId, result.evaluationId)
        assertEquals(1, repository.evaluations.size)
        assertEquals(CreditDecisionStatus.APPROVED, result.decision)
    }

    @Test
    // @spec:AC-003
    fun `AC-003 returns a rejected decision instead of a technical error`() {
        val repository = RecordingRepository()
        var calculatorCalled = false
        val calculator = calculator { calculatorCalled = true; BigDecimal("999.99") }

        val result = useCase(blockingFailure(), repository, calculator).execute(validCommand())

        assertEquals(CreditDecisionStatus.REJECTED, result.decision)
        assertEquals(BigDecimal.ZERO, result.approvedAmount)
        assertEquals(false, calculatorCalled)
    }

    @Test
    // @spec:AC-015
    fun `AC-015 returns all decision traceability fields with a masked CPF`() {
        val result = useCase(passingRule(), RecordingRepository()).execute(validCommand())

        assertEquals(fixedId, result.evaluationId)
        assertEquals("***.***.***-09", result.maskedCpf)
        assertEquals(CreditDecisionStatus.APPROVED, result.decision)
        assertEquals(BigDecimal("700.00"), result.approvedAmount)
        assertEquals("rules-v1", result.ruleSetVersion)
        assertEquals(1, result.ruleResults.size)
        assertEquals(instant, result.processedAt)
        assertEquals(0, result.processingTimeMs)
        assertEquals("corr-123", result.correlationId)
    }

    @Test
    // @spec:AC-016
    fun `AC-016 persists an immutable photograph of the completed decision`() {
        val repository = RecordingRepository()
        val result = useCase(passingRule(), repository).execute(validCommand())

        val persisted = repository.evaluations.single()
        assertEquals(result, persisted)
        assertEquals("rules-v1", persisted.ruleSetVersion)
        assertEquals(1, persisted.ruleResults.size)
        assertNotEquals("12345678909", persisted.maskedCpf)
    }

    private fun useCase(
        rule: CreditRule,
        repository: RecordingRepository,
        calculator: CreditLimitCalculator = calculator { BigDecimal("700.00") },
    ) = EvaluateRevolvingCreditUseCase(
        ruleEngine = RuleEngine(listOf(rule)),
        creditLimitCalculator = calculator,
        repository = repository,
        clock = Clock.fixed(instant, ZoneOffset.UTC),
        idGenerator = { fixedId },
        ruleSetVersion = "rules-v1",
    )

    private fun passingRule() = rule(RuleStatus.PASSED)

    private fun blockingFailure() = rule(RuleStatus.FAILED)

    private fun rule(status: RuleStatus) = object : CreditRule {
        override val code = "MINIMUM_SCORE"
        override val name = "Minimum score"
        override val severity = RuleSeverity.BLOCKING
        override fun evaluate(context: CreditEvaluationContext) = RuleResult(
            code, name, severity, status, if (status == RuleStatus.PASSED) "passed" else "failed",
        )
    }

    private fun calculator(block: () -> BigDecimal) = object : CreditLimitCalculator {
        override fun calculate(availableLimit: BigDecimal, creditScore: Int, eligible: Boolean): BigDecimal = block()
    }

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

    private class RecordingRepository : CreditEvaluationRepository {
        val evaluations = mutableListOf<CreditEvaluation>()

        override fun save(evaluation: CreditEvaluation): CreditEvaluation = evaluation.also(evaluations::add)

        override fun findById(evaluationId: UUID): CreditEvaluation? = evaluations.find { it.evaluationId == evaluationId }

        override fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest): CreditEvaluationPage =
            CreditEvaluationPage(evaluations, evaluations.size.toLong(), page.page, page.size, page.sort)
    }
}
