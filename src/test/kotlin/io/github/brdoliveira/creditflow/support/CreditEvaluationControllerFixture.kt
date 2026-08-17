package io.github.brdoliveira.creditflow.support

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationResult
import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPage
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.application.EvaluateRevolvingCreditUseCase
import io.github.brdoliveira.creditflow.evaluation.application.FindCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.ListCreditEvaluationsUseCase
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationMetrics
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyExecution
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyRepository
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus
import io.github.brdoliveira.creditflow.evaluation.domain.calculation.CreditLimitCalculator
import io.github.brdoliveira.creditflow.evaluation.domain.rule.CreditRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RuleEngine
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller.CreditEvaluationController
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.mapper.CreditEvaluationWebMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

object CreditEvaluationControllerFixture {
    val evaluationId: UUID = UUID.fromString("d2719d1c-f0db-4c3d-9de2-4d7cfd6d4d7e")

    fun controller(
        evaluation: CreditEvaluation = evaluation(),
        found: CreditEvaluation? = evaluation,
        failure: RuntimeException? = null,
    ): CreditEvaluationController {
        val repository = FakeRepository(evaluation, found, failure)
        val evaluator = EvaluateRevolvingCreditUseCase(
            RuleEngine(listOf(PassingRule)),
            FixedCalculator,
            repository,
            Clock.fixed(evaluation.processedAt, ZoneOffset.UTC),
            { evaluation.evaluationId },
            evaluation.ruleSetVersion,
        )
        val create = CreateCreditEvaluationUseCase(
            evaluator,
            object : IdempotencyRepository {
                override fun execute(
                    key: String?,
                    requestBody: String,
                    operation: () -> CreateCreditEvaluationResult,
                ) = IdempotencyExecution(operation(), replayed = false)
            },
            CreditEvaluationMetrics { _, _ -> },
        )
        return CreditEvaluationController(
            create,
            FindCreditEvaluationUseCase(repository),
            ListCreditEvaluationsUseCase(repository),
            CreditEvaluationWebMapper(),
        )
    }

    fun evaluation(
        decision: CreditDecisionStatus = CreditDecisionStatus.APPROVED,
        approvedAmount: BigDecimal = BigDecimal("2800.00"),
    ) = CreditEvaluation(
        evaluationId,
        "***.982.247-**",
        decision,
        listOf(RuleResult("MINIMUM_SCORE", "Minimum score", RuleSeverity.BLOCKING, RuleStatus.PASSED, "Score meets the threshold")),
        approvedAmount,
        "v1",
        Instant.parse("2026-08-15T10:00:00Z"),
        21,
        "trace-1",
    )

    private object PassingRule : CreditRule {
        override val code = "MINIMUM_SCORE"
        override val name = "Minimum score"
        override val severity = RuleSeverity.BLOCKING
        override fun evaluate(context: CreditEvaluationContext) =
            RuleResult(code, name, severity, RuleStatus.PASSED, "Score meets the threshold")
    }

    private object FixedCalculator : CreditLimitCalculator {
        override fun calculate(availableLimit: BigDecimal, creditScore: Int, eligible: Boolean) = BigDecimal("2800.00")
    }

    private class FakeRepository(
        private val evaluation: CreditEvaluation,
        private val found: CreditEvaluation?,
        private val failure: RuntimeException?,
    ) : CreditEvaluationRepository {
        override fun save(evaluation: CreditEvaluation): CreditEvaluation = failure?.let { throw it } ?: this.evaluation
        override fun findById(evaluationId: UUID): CreditEvaluation? = failure?.let { throw it } ?: found
        override fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest): CreditEvaluationPage {
            failure?.let { throw it }
            return CreditEvaluationPage(listOf(evaluation), 1, page.page, page.size, page.sort)
        }
    }
}
