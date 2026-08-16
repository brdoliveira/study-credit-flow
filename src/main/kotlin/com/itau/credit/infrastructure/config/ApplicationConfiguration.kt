package com.itau.credit.infrastructure.config

import com.itau.credit.application.evaluation.CreditEvaluationSnapshotStore
import com.itau.credit.application.evaluation.CreditEvaluationSnapshot as EvaluationSnapshot
import com.itau.credit.application.evaluation.CreditRuleEvaluator
import com.itau.credit.application.evaluation.EvaluateRevolvingCreditUseCase
import com.itau.credit.application.evaluation.ExecutedRule
import com.itau.credit.application.evaluation.RevolvingCreditCalculator
import com.itau.credit.application.evaluation.RuleEvaluation
import com.itau.credit.application.port.CreditEvaluationRepository
import com.itau.credit.application.port.CreditEvaluationSnapshot as StoredSnapshot
import com.itau.credit.application.report.CreditEvaluationReportDataSource
import com.itau.credit.domain.calculation.ConfigurableCreditLimitCalculator
import com.itau.credit.domain.model.CreditEvaluationContext
import com.itau.credit.domain.rule.AvailableLimitRule
import com.itau.credit.domain.rule.LimitCommitmentRule
import com.itau.credit.domain.rule.MaxLatePaymentsRule
import com.itau.credit.domain.rule.MinimumScoreRule
import com.itau.credit.domain.rule.RecentSpendingTrendRule
import com.itau.credit.domain.rule.RuleEngine
import com.itau.credit.infrastructure.health.DependencyReadinessIndicator
import com.itau.credit.infrastructure.health.RequiredDependencyProbe
import com.itau.credit.infrastructure.observability.CreditMetrics
import com.itau.credit.infrastructure.report.PdfCreditEvaluationReportGenerator
import com.itau.credit.infrastructure.web.CreditEvaluationReportService
import com.itau.credit.infrastructure.web.DefaultCreditEvaluationReportService
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun creditRuleEvaluator(): CreditRuleEvaluator {
        val engine = RuleEngine(
            listOf(
                MinimumScoreRule(),
                MaxLatePaymentsRule(),
                AvailableLimitRule(),
                LimitCommitmentRule(),
                RecentSpendingTrendRule(),
            )
        )
        return CreditRuleEvaluator { command ->
            val decision = engine.evaluate(
                CreditEvaluationContext(
                    command.customerName,
                    command.cpf,
                    command.creditScore,
                    command.currentInvoiceAmount,
                    command.totalLimit,
                    command.availableLimit,
                    command.latePayments,
                    command.monthlySpending,
                )
            )
            RuleEvaluation(
                RULE_VERSION,
                decision.ruleResults.map { rule ->
                    ExecutedRule(
                        rule.code,
                        rule.name,
                        com.itau.credit.application.evaluation.RuleSeverity.valueOf(rule.severity.name),
                        com.itau.credit.application.evaluation.RuleStatus.valueOf(rule.status.name),
                        rule.reason,
                    )
                },
            )
        }
    }

    @Bean
    fun revolvingCreditCalculator(): RevolvingCreditCalculator {
        val calculator = ConfigurableCreditLimitCalculator()
        return RevolvingCreditCalculator { command -> calculator.calculate(command.availableLimit, command.creditScore) }
    }

    @Bean
    fun snapshotStore(repository: CreditEvaluationRepository, objectMapper: ObjectMapper): CreditEvaluationSnapshotStore =
        object : CreditEvaluationSnapshotStore {
            override fun save(snapshot: EvaluationSnapshot): EvaluationSnapshot {
                repository.save(
                    StoredSnapshot(
                        snapshot.evaluationId,
                        snapshot.maskedCpf,
                        snapshot.decision.name,
                        snapshot.approvedAmount,
                        snapshot.ruleSetVersion,
                        objectMapper.writeValueAsString(snapshot.executedRules),
                        snapshot.processedAt,
                        snapshot.processingTimeMs,
                        snapshot.correlationId,
                    )
                )
                return snapshot
            }
        }

    @Bean
    fun evaluateRevolvingCreditUseCase(
        ruleEvaluator: CreditRuleEvaluator,
        calculator: RevolvingCreditCalculator,
        snapshotStore: CreditEvaluationSnapshotStore,
        clock: Clock,
    ) = EvaluateRevolvingCreditUseCase(ruleEvaluator, calculator, snapshotStore, clock)

    @Bean
    fun pdfCreditEvaluationReportGenerator() = PdfCreditEvaluationReportGenerator()

    @Bean
    fun reportDataSource(repository: CreditEvaluationRepository): CreditEvaluationReportDataSource =
        CreditEvaluationReportDataSource { filter -> reportRows(repository, filter) }

    @Bean
    fun creditEvaluationReportService(
        dataSource: CreditEvaluationReportDataSource,
        generator: PdfCreditEvaluationReportGenerator,
    ): CreditEvaluationReportService = DefaultCreditEvaluationReportService(dataSource, generator)

    @Bean
    fun creditMetrics(registry: MeterRegistry) = CreditMetrics(registry)

    @Bean("dependencyReadiness")
    fun dependencyReadiness(dataSource: DataSource): HealthIndicator = DependencyReadinessIndicator(
        mapOf("postgres" to RequiredDependencyProbe { dataSource.connection.use { it.isValid(2) } })
    )

    companion object {
        const val RULE_VERSION = "2026.08"
    }
}

private fun reportRows(
    repository: CreditEvaluationRepository,
    filter: com.itau.credit.application.report.CreditEvaluationReportFilter,
): List<com.itau.credit.application.report.CreditEvaluationReportRow> {
    val storedFilter = com.itau.credit.application.port.CreditEvaluationFilter(
        filter.decision,
        filter.from?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant(),
        filter.to?.plusDays(1)?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.minusNanos(1),
    )
    val rows = mutableListOf<com.itau.credit.application.report.CreditEvaluationReportRow>()
    var pageNumber = 0
    do {
        val page = repository.findPage(
            storedFilter,
            com.itau.credit.application.port.CreditEvaluationPageRequest(pageNumber, 100),
        )
        rows += page.items.map {
            com.itau.credit.application.report.CreditEvaluationReportRow(
                it.evaluationId,
                it.maskedCpf,
                it.decision,
                it.approvedAmount,
                it.evaluatedAt,
            )
        }
        pageNumber++
    } while (rows.size < page.total)
    return rows
}
