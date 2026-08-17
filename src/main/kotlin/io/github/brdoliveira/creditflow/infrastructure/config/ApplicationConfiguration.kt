package io.github.brdoliveira.creditflow.infrastructure.config

import io.github.brdoliveira.creditflow.application.evaluation.CreditEvaluationSnapshotStore
import io.github.brdoliveira.creditflow.application.evaluation.CreditEvaluationSnapshot as EvaluationSnapshot
import io.github.brdoliveira.creditflow.application.evaluation.CreditRuleEvaluator
import io.github.brdoliveira.creditflow.application.evaluation.EvaluateRevolvingCreditUseCase
import io.github.brdoliveira.creditflow.application.evaluation.ExecutedRule
import io.github.brdoliveira.creditflow.application.evaluation.RevolvingCreditCalculator
import io.github.brdoliveira.creditflow.application.evaluation.RuleEvaluation
import io.github.brdoliveira.creditflow.application.evaluation.RuleSeverity as ApplicationRuleSeverity
import io.github.brdoliveira.creditflow.application.evaluation.RuleStatus as ApplicationRuleStatus
import io.github.brdoliveira.creditflow.application.port.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.application.port.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.application.port.CreditEvaluationSnapshot as StoredSnapshot
import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReportDataSource
import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReportFilter
import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReportRow
import io.github.brdoliveira.creditflow.domain.calculation.ConfigurableCreditLimitCalculator
import io.github.brdoliveira.creditflow.domain.model.CreditEvaluationContext
import io.github.brdoliveira.creditflow.domain.rule.AvailableLimitRule
import io.github.brdoliveira.creditflow.domain.rule.LimitCommitmentRule
import io.github.brdoliveira.creditflow.domain.rule.MaxLatePaymentsRule
import io.github.brdoliveira.creditflow.domain.rule.MinimumScoreRule
import io.github.brdoliveira.creditflow.domain.rule.RecentSpendingTrendRule
import io.github.brdoliveira.creditflow.domain.rule.RuleEngine
import io.github.brdoliveira.creditflow.infrastructure.health.DependencyReadinessIndicator
import io.github.brdoliveira.creditflow.infrastructure.health.RequiredDependencyProbe
import io.github.brdoliveira.creditflow.infrastructure.messaging.BrokerPublisher
import io.github.brdoliveira.creditflow.infrastructure.messaging.CreditEvaluationEventEffect
import io.github.brdoliveira.creditflow.infrastructure.messaging.CreditEvaluationKafkaListener
import io.github.brdoliveira.creditflow.infrastructure.messaging.KafkaBrokerPublisher
import io.github.brdoliveira.creditflow.infrastructure.messaging.PostgresProcessedEventStore
import io.github.brdoliveira.creditflow.infrastructure.messaging.ProcessedEventStore
import io.github.brdoliveira.creditflow.infrastructure.observability.CreditMetrics
import io.github.brdoliveira.creditflow.infrastructure.outbox.OutboxSchedulingConfiguration
import io.github.brdoliveira.creditflow.infrastructure.outbox.OutboxStore
import io.github.brdoliveira.creditflow.infrastructure.outbox.PostgresOutboxStore
import io.github.brdoliveira.creditflow.infrastructure.report.PdfCreditEvaluationReportGenerator
import io.github.brdoliveira.creditflow.infrastructure.web.CreditEvaluationReportService
import io.github.brdoliveira.creditflow.infrastructure.web.DefaultCreditEvaluationReportService
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@Import(OutboxSchedulingConfiguration::class)
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
                        ApplicationRuleSeverity.valueOf(rule.severity.name),
                        ApplicationRuleStatus.valueOf(rule.status.name),
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

    @Bean
    fun outboxStore(jdbcTemplate: JdbcTemplate, objectMapper: ObjectMapper): OutboxStore =
        PostgresOutboxStore(jdbcTemplate, objectMapper)

    @Bean
    fun brokerPublisher(kafkaTemplate: KafkaTemplate<String, String>): BrokerPublisher =
        KafkaBrokerPublisher(kafkaTemplate)

    @Bean
    fun processedEventStore(jdbcTemplate: JdbcTemplate, transactions: TransactionTemplate): ProcessedEventStore =
        PostgresProcessedEventStore(jdbcTemplate, transactions)

    @Bean
    fun creditEvaluationKafkaListener(
        objectMapper: ObjectMapper,
        processedEventStore: ProcessedEventStore,
        eventEffect: ObjectProvider<CreditEvaluationEventEffect>,
    ) = CreditEvaluationKafkaListener(objectMapper, processedEventStore, eventEffect)

    @Bean("dependencyReadiness")
    fun dependencyReadiness(
        dataSource: DataSource,
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
    ): HealthIndicator = DependencyReadinessIndicator(
        mapOf(
            "postgres" to RequiredDependencyProbe { dataSource.connection.use { it.isValid(2) } },
            "kafka" to RequiredDependencyProbe {
                AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use { client ->
                    client.describeCluster().clusterId().get(2, TimeUnit.SECONDS).isNotBlank()
                }
            },
        )
    )

    companion object {
        const val RULE_VERSION = "2026.08"
    }
}

private fun reportRows(
    repository: CreditEvaluationRepository,
    filter: CreditEvaluationReportFilter,
): List<CreditEvaluationReportRow> {
    val storedFilter = CreditEvaluationFilter(
        filter.decision,
        filter.from?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant(),
        filter.to?.plusDays(1)?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.minusNanos(1),
    )
    val rows = mutableListOf<CreditEvaluationReportRow>()
    var pageNumber = 0
    do {
        val page = repository.findPage(
            storedFilter,
            CreditEvaluationPageRequest(pageNumber, 100),
        )
        rows += page.items.map {
            CreditEvaluationReportRow(
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
