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
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** Compõe casos de uso, domínio e adaptadores transversais do Spring. */
@Configuration(proxyBeanMethods = false)
@Import(OutboxSchedulingConfiguration::class)
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

    /** Fornece o armazenamento PostgreSQL da Outbox. */
    @Bean
    fun outboxStore(jdbcTemplate: JdbcTemplate, objectMapper: ObjectMapper): OutboxStore =
        PostgresOutboxStore(jdbcTemplate, objectMapper)

    /** Fornece o publicador síncrono para o broker Kafka. */
    @Bean
    fun brokerPublisher(kafkaTemplate: KafkaTemplate<String, String>): BrokerPublisher =
        KafkaBrokerPublisher(kafkaTemplate)

    /** Fornece o armazenamento de eventos já processados. */
    @Bean
    fun processedEventStore(
        jdbcTemplate: JdbcTemplate,
        transactions: TransactionTemplate,
    ): ProcessedEventStore = PostgresProcessedEventStore(jdbcTemplate, transactions)

    /** Compõe o listener Kafka idempotente. */
    @Bean
    fun creditEvaluationKafkaListener(
        objectMapper: ObjectMapper,
        processedEventStore: ProcessedEventStore,
        eventEffect: ObjectProvider<CreditEvaluationEventEffect>,
    ) = CreditEvaluationKafkaListener(objectMapper, processedEventStore, eventEffect)

    /** Verifica PostgreSQL e Kafka sem misturá-los à liveness do processo. */
    @Bean("dependencyReadiness")
    fun dependencyReadiness(
        dataSource: DataSource,
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
    ): HealthIndicator = DependencyReadinessIndicator(
        mapOf(
            "postgres" to RequiredDependencyProbe { dataSource.connection.use { it.isValid(2) } },
            "kafka" to RequiredDependencyProbe {
                AdminClient.create(
                    mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers),
                ).use { client ->
                    client.describeCluster().clusterId().get(2, TimeUnit.SECONDS).isNotBlank()
                }
            },
        ),
    )

    /** Constantes de versionamento do conjunto de regras. */
    companion object {
        const val RULE_VERSION = "2026.08"
    }
}
