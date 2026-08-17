package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.github.brdoliveira.creditflow.evaluation.application.event.CreditEvaluationCompleted
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.OutboxPublisher
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.OutboxStore
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.PendingOutboxEvent
import io.github.brdoliveira.creditflow.evaluation.infrastructure.observability.AsyncProcessingMetrics
import io.github.brdoliveira.creditflow.evaluation.infrastructure.observability.MicrometerAsyncProcessingMetrics
import io.github.brdoliveira.creditflow.platform.observability.CorrelationIdFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.support.StaticListableBeanFactory
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith

class AsyncOperationalLoggingTest {
    private val event = CreditEvaluationCompleted(
        eventId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        evaluationId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        decision = "APPROVED",
        approvedAmount = BigDecimal("1500.50"),
        ruleVersion = "token-secret-value",
        evaluatedAt = Instant.parse("2026-08-16T12:00:00Z"),
        correlationId = "correlation-044",
    )

    @Test
    // @spec:AC-098
    fun `AC-098 outbox retry logs correlated warning without serializing event payload`() {
        val store = CapturingOutboxStore(PendingOutboxEvent(event.eventId, event, attempts = 2))
        val registry = SimpleMeterRegistry()
        val publisher = OutboxPublisher(
            store,
            CreditEvaluationEventProducer(
                BrokerPublisher { _, _, _ -> throw TransientBrokerException("broker token=do-not-log") },
                ObjectMapper(),
            ),
            Clock.fixed(event.evaluatedAt, ZoneOffset.UTC),
            Duration.ofSeconds(2),
            Duration.ofMinutes(5),
            metrics = MicrometerAsyncProcessingMetrics(registry),
        )

        val logs = captureLogs(OutboxPublisher::class.java) { publisher.publishPending() }

        assertThat(store.rescheduled).isEqualTo(Reschedule(3, event.evaluatedAt.plusSeconds(8)))
        assertThat(registry.counter("credit.outbox.events", "outcome", "retry").count()).isEqualTo(1.0)
        val log = logs.single()
        assertThat(log.level).isEqualTo(Level.WARN)
        assertThat(log.mdcPropertyMap).containsEntry(CorrelationIdFilter.MDC_KEY, event.correlationId)
        assertThat(log.formattedMessage).contains(event.eventId.toString(), "attempt=3", "nextAttemptAt=", "TransientBrokerException")
            .doesNotContain("1500.50", "token-secret-value", "do-not-log")
    }

    @Test
    // @spec:AC-099
    fun `AC-099 Kafka consumption keeps correlation and logs processed duplicate and failure outcomes`() {
        val payload = ObjectMapper().writeValueAsString(event)
        val registry = SimpleMeterRegistry()
        val metrics = MicrometerAsyncProcessingMetrics(registry)
        val seenCorrelations = mutableListOf<String?>()
        val listener = listener(
            ProcessedEventStore { _, effect -> effect(); true },
            CreditEvaluationEventEffect { seenCorrelations += MDC.get(CorrelationIdFilter.MDC_KEY) },
            metrics,
        )
        val duplicateListener = listener(ProcessedEventStore { _, _ -> false }, CreditEvaluationEventEffect { }, metrics)
        val failingListener = listener(
            ProcessedEventStore { _, effect -> effect(); true },
            CreditEvaluationEventEffect { throw IllegalStateException("cpf=12345678909") },
            metrics,
        )
        MDC.put(CorrelationIdFilter.MDC_KEY, "caller-correlation")

        try {
            val logs = captureLogs(CreditEvaluationKafkaListener::class.java) {
                assertThat(listener.onPayload(payload)).isEqualTo(ConsumptionResult.PROCESSED)
                assertThat(duplicateListener.onPayload(payload)).isEqualTo(ConsumptionResult.DUPLICATE_ACKNOWLEDGED)
                assertFailsWith<IllegalStateException> { failingListener.onPayload(payload) }
            }

            assertThat(seenCorrelations).containsExactly(event.correlationId)
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("caller-correlation")
            assertThat(logs.map { it.level }).containsExactlyInAnyOrder(Level.DEBUG, Level.INFO, Level.ERROR)
            assertThat(logs).allSatisfy { log ->
                assertThat(log.mdcPropertyMap).containsEntry(CorrelationIdFilter.MDC_KEY, event.correlationId)
                assertThat(log.formattedMessage).contains(event.eventId.toString())
                    .doesNotContain("1500.50", "token-secret-value", "12345678909")
            }
            assertThat(registry.counter("credit.kafka.events", "outcome", "processed").count()).isEqualTo(1.0)
            assertThat(registry.counter("credit.kafka.events", "outcome", "duplicate").count()).isEqualTo(1.0)
            assertThat(registry.counter("credit.kafka.events", "outcome", "failed").count()).isEqualTo(1.0)
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY)
        }
    }

    @Test
    // @spec:AC-101
    fun `AC-101 successful asynchronous operations avoid INFO logs and sensitive event values`() {
        val payload = ObjectMapper().writeValueAsString(event)
        val listener = listener(ProcessedEventStore { _, effect -> effect(); true }, CreditEvaluationEventEffect { })

        val log = captureLogs(CreditEvaluationKafkaListener::class.java) { listener.onPayload(payload) }.single()

        assertThat(log.level).isEqualTo(Level.DEBUG)
        assertThat(log.formattedMessage).doesNotContain("1500.50", "token-secret-value", payload)
    }

    private fun listener(
        store: ProcessedEventStore,
        effect: CreditEvaluationEventEffect,
        metrics: AsyncProcessingMetrics = AsyncProcessingMetrics.NONE,
    ): CreditEvaluationKafkaListener {
        val provider = StaticListableBeanFactory().apply { addBean("effect", effect) }
            .getBeanProvider(CreditEvaluationEventEffect::class.java)
        return CreditEvaluationKafkaListener(ObjectMapper(), store, provider, metrics)
    }

    private fun captureLogs(loggerClass: Class<*>, action: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        val originalLevel = logger.level
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.DEBUG
        return try {
            action()
            appender.list.toList()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            logger.level = originalLevel
        }
    }

    private data class Reschedule(val attempts: Int, val nextAttemptAt: Instant)

    private class CapturingOutboxStore(private val pendingEvent: PendingOutboxEvent) : OutboxStore {
        var rescheduled: Reschedule? = null

        override fun pending(now: Instant, limit: Int): List<PendingOutboxEvent> = listOf(pendingEvent)
        override fun markPublished(eventId: UUID, publishedAt: Instant) = Unit
        override fun reschedule(eventId: UUID, attempts: Int, nextAttemptAt: Instant, reason: String) {
            rescheduled = Reschedule(attempts, nextAttemptAt)
        }
        override fun markFailed(eventId: UUID, attempts: Int, failedAt: Instant, reason: String) = Unit
    }
}
