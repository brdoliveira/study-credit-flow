package com.itau.credit.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.itau.credit.application.event.CreditEvaluationCompleted
import com.itau.credit.infrastructure.outbox.OutboxPublisher
import com.itau.credit.infrastructure.outbox.OutboxStore
import com.itau.credit.infrastructure.outbox.PendingOutboxEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CreditEvaluationMessagingIT {
    private val now = Instant.parse("2026-08-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `@spec:AC-033 evaluation insert is atomically accompanied by an outbox insert`() {
        val migration = javaClass.getResource("/db/migration/V3__credit_outbox.sql")!!.readText()

        assertThat(migration).contains("AFTER INSERT ON credit_evaluation", "INSERT INTO credit_outbox")
        assertThat(migration).contains("NEW.evaluation_id", "REFERENCES credit_evaluation")
    }

    @Test
    fun `@spec:AC-034 serialized event is versioned and never exposes a complete CPF`() {
        val event = event()
        val json = ObjectMapper().registerKotlinModule().writeValueAsString(event)

        assertThat(json).contains("eventId", "eventVersion", "evaluationId", "decision", "approvedAmount", "ruleVersion", "evaluatedAt", "correlationId")
        assertThat(json).doesNotContain("12345678909", "cpf", "CPF")
    }

    @Test
    fun `@spec:AC-035 transient publication failure stays pending and is retried with bounded backoff`() {
        val store = InMemoryOutboxStore(PendingOutboxEvent(event().eventId, event(), attempts = 0))
        var calls = 0
        val producer = CreditEvaluationEventProducer(
            broker = BrokerPublisher { _, _, _ ->
                calls++
                if (calls == 1) throw TransientBrokerException("broker unavailable")
            },
            objectMapper = ObjectMapper().registerKotlinModule(),
        )
        val publisher = OutboxPublisher(store, producer, clock, Duration.ofSeconds(1), Duration.ofSeconds(2))

        publisher.publishPending()
        assertThat(store.published).isEmpty()
        assertThat(store.retry).isEqualTo(Retry(1, now.plusSeconds(1), "broker unavailable"))

        store.now = now.plusSeconds(1)
        publisher.publishPending()
        assertThat(store.published).containsExactly(store.event.eventId)
    }

    @Test
    fun `@spec:AC-036 duplicate event is acknowledged without repeating its effect`() {
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
        correlationId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
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
