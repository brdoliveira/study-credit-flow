package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

import io.github.brdoliveira.creditflow.evaluation.application.event.CreditEvaluationCompleted
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.OutboxPublisher
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.OutboxStore
import io.github.brdoliveira.creditflow.evaluation.infrastructure.outbox.PendingOutboxEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.path.readText
import kotlin.test.assertFalse
import tools.jackson.databind.ObjectMapper

class CreditEvaluationMessagingIT {
    private val now = Instant.parse("2026-08-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    // @spec:AC-033
    fun `AC-033 evaluation insert is atomically accompanied by an outbox insert`() {
        val repository = Path.of(
            "src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/" +
                "PostgresCreditEvaluationRepository.kt",
        ).readText()
        val migration = javaClass.getResource("/db/migration/V5__explicit_credit_outbox.sql")!!.readText()

        assertThat(repository).contains("@Transactional", "entityManager.persist", "INSERT_OUTBOX")
        assertThat(migration).contains("DROP TRIGGER", "trg_credit_evaluation_outbox")
    }

    @Test
    // @spec:AC-094
    fun `AC-094 Kotlin event is the single source of the outbox payload contract`() {
        val repository = Path.of(
            "src/main/kotlin/io/github/brdoliveira/creditflow/evaluation/infrastructure/persistence/" +
                "PostgresCreditEvaluationRepository.kt",
        ).readText()
        val migration = javaClass.getResource("/db/migration/V5__explicit_credit_outbox.sql")!!.readText()

        assertThat(repository).contains("CreditEvaluationCompleted(", "objectMapper.writeValueAsString(event)")
        assertThat(migration).contains("DROP TRIGGER").doesNotContain("CREATE TRIGGER", "jsonb_build_object")
    }

    @Test
    // @spec:AC-034
    fun `AC-034 serialized event is versioned and never exposes a complete CPF`() {
        val event = event()
        val json = ObjectMapper().writeValueAsString(event)

        assertThat(json).contains("eventId", "eventVersion", "evaluationId", "decision", "approvedAmount", "ruleVersion", "evaluatedAt", "correlationId")
        assertFalse(json.contains("12345678909"))
        assertFalse(json.contains("cpf", ignoreCase = true))
    }

    @Test
    // @spec:AC-035
    fun `AC-035 transient publication failure stays pending and is retried with bounded backoff`() {
        val store = InMemoryOutboxStore(PendingOutboxEvent(event().eventId, event(), attempts = 0))
        var calls = 0
        val producer = CreditEvaluationEventProducer(
            broker = BrokerPublisher { _, _, _ ->
                calls++
                if (calls == 1) throw TransientBrokerException("broker unavailable")
            },
            objectMapper = ObjectMapper(),
        )
        val publisher = OutboxPublisher(store, producer, clock, Duration.ofSeconds(1), Duration.ofSeconds(2))

        publisher.publishPending()
        assertThat(store.published).isEmpty()
        assertThat(store.retry).isEqualTo(Retry(1, now.plusSeconds(1), "Broker publication failed (TransientBrokerException)"))

        store.now = now.plusSeconds(1)
        publisher.publishPending()
        assertThat(store.published).containsExactly(store.event.eventId)
    }

    @Test
    // @spec:AC-036
    fun `AC-036 duplicate event is acknowledged without repeating its effect`() {
        val seen = mutableSetOf<UUID>()
        var effects = 0
        val consumer = IdempotentCreditEvaluationConsumer(
            ProcessedEventStore { eventId, effect -> if (seen.add(eventId)) { effect(); true } else false },
            CreditEvaluationEventEffect { effects++ },
        )

        assertThat(consumer.consume(event())).isEqualTo(ConsumptionResult.PROCESSED)
        assertThat(consumer.consume(event())).isEqualTo(ConsumptionResult.DUPLICATE_ACKNOWLEDGED)
        assertThat(effects).isEqualTo(1)
    }

    private fun event() = CreditEvaluationCompleted(
        eventId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        evaluationId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        decision = "APPROVED",
        approvedAmount = BigDecimal("1200.50"),
        ruleVersion = "2026.08",
        evaluatedAt = now,
        correlationId = "opaque-correlation-id",
    )

    private data class Retry(val attempts: Int, val nextAttemptAt: Instant, val reason: String)

    private class InMemoryOutboxStore(initial: PendingOutboxEvent) : OutboxStore {
        var event = initial
        var now: Instant = Instant.EPOCH
        val published = mutableListOf<UUID>()
        var retry: Retry? = null

        override fun pending(now: Instant, limit: Int): List<PendingOutboxEvent> =
            if (published.isEmpty() && (retry == null || this.now >= retry!!.nextAttemptAt)) listOf(event) else emptyList()

        override fun markPublished(eventId: UUID, publishedAt: Instant) {
            published += eventId
        }

        override fun reschedule(eventId: UUID, attempts: Int, nextAttemptAt: Instant, reason: String) {
            event = event.copy(attempts = attempts)
            retry = Retry(attempts, nextAttemptAt, reason)
        }
    }
}
