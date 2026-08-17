package io.github.brdoliveira.creditflow.infrastructure.config

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.EvaluateRevolvingCreditUseCase
import io.github.brdoliveira.creditflow.evaluation.application.FindCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.ListCreditEvaluationsUseCase
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyRepository
import io.github.brdoliveira.creditflow.evaluation.application.report.CreditEvaluationReportGenerator
import io.github.brdoliveira.creditflow.evaluation.application.report.GenerateCreditEvaluationReportUseCase
import io.github.brdoliveira.creditflow.evaluation.domain.calculation.ConfigurableCreditLimitCalculator
import io.github.brdoliveira.creditflow.evaluation.domain.calculation.CreditLimitCalculator
import io.github.brdoliveira.creditflow.evaluation.domain.rule.AvailableLimitRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.LimitCommitmentRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.MaxLatePaymentsRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.MinimumScoreRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RecentSpendingTrendRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RuleEngine
import io.github.brdoliveira.creditflow.evaluation.infrastructure.report.PdfCreditEvaluationReportGenerator
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.mapper.CreditEvaluationWebMapper
import io.github.brdoliveira.creditflow.evaluation.infrastructure.observability.CreditMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Compõe o domínio e os casos de uso da avaliação no Spring. */
@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {
    /** Fornece o relógio UTC compartilhado pelos casos de uso. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** Registra todas as regras ativas na ordem de execução. */
    @Bean
    fun ruleEngine() = RuleEngine(
        listOf(
            MinimumScoreRule(),
            MaxLatePaymentsRule(),
            AvailableLimitRule(),
            LimitCommitmentRule(),
            RecentSpendingTrendRule(),
        ),
    )

    /** Fornece a calculadora pura do domínio. */
    @Bean
    fun creditLimitCalculator(): CreditLimitCalculator = ConfigurableCreditLimitCalculator()

    /** Compõe o caso de uso que executa e persiste a avaliação. */
    @Bean
    fun evaluateRevolvingCreditUseCase(
        ruleEngine: RuleEngine,
        calculator: CreditLimitCalculator,
        repository: CreditEvaluationRepository,
        clock: Clock,
    ) = EvaluateRevolvingCreditUseCase(ruleEngine, calculator, repository, clock, ruleSetVersion = RULE_VERSION)

    /** Compõe o caso de uso de consulta por identificador. */
    @Bean
    fun findCreditEvaluationUseCase(repository: CreditEvaluationRepository) =
        FindCreditEvaluationUseCase(repository)

    /** Compõe o caso de uso de listagem. */
    @Bean
    fun listCreditEvaluationsUseCase(repository: CreditEvaluationRepository) =
        ListCreditEvaluationsUseCase(repository)

    /** Fornece o mapeador da fronteira HTTP. */
    @Bean
    fun creditEvaluationWebMapper() = CreditEvaluationWebMapper()

    /** Fornece o gerador concreto de relatórios PDF. */
    @Bean
    fun creditEvaluationReportGenerator(): CreditEvaluationReportGenerator =
        PdfCreditEvaluationReportGenerator()

    /** Compõe a geração de relatório a partir da listagem paginada. */
    @Bean
    fun generateCreditEvaluationReportUseCase(
        list: ListCreditEvaluationsUseCase,
        generator: CreditEvaluationReportGenerator,
        clock: Clock,
    ) = GenerateCreditEvaluationReportUseCase(list, generator, clock)

    /** Fornece as métricas técnicas e de negócio. */
    @Bean
    fun creditMetrics(registry: MeterRegistry) = CreditMetrics(registry)

    /** Compõe criação, idempotência e métricas. */
    @Bean
    fun createCreditEvaluationUseCase(
        evaluator: EvaluateRevolvingCreditUseCase,
        idempotencyRepository: IdempotencyRepository,
        metrics: CreditMetrics,
    ) = CreateCreditEvaluationUseCase(evaluator, idempotencyRepository, metrics)

    /** Constantes de versionamento do conjunto de regras. */
    companion object {
        const val RULE_VERSION = "2026.08"
    }
}
