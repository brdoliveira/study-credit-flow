package com.itau.credit.infrastructure.messaging

import com.itau.credit.CreditFlowApplication
import com.itau.credit.application.event.CreditEvaluationCompleted
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest(classes = [CreditFlowApplication::class])
class IdempotentKafkaConsumerIT {
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var processedEventStore: ProcessedEventStore
    private lateinit var listener: CreditEvaluationKafkaListener

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS consumed_credit_evaluation_effect (event_id UUID PRIMARY KEY)")
        jdbcTemplate.execute("DELETE FROM consumed_credit_evaluation_effect")
        jdbcTemplate.execute("DELETE FROM processed_credit_evaluation_event")
        val effect = CreditEvaluationEventEffect { event ->
            jdbcTemplate.update("INSERT INTO consumed_credit_evaluation_effect (event_id) VALUES (?)", event.eventId)
        }
        listener = CreditEvaluationKafkaListener(objectMapper, processedEventStore, providerOf(effect))
    }

    @Test
    // @spec:AC-059
    fun `AC-059 Kafka duplicate delivery persists its marker and applies the effect only once`() {
        val event = event()
        val payload = objectMapper.writeValueAsString(event)
        listener.onMessage(ConsumerRecord("credit.evaluation.completed.v1", 0, 0, event.eventId.toString(), payload))
        listener.onMessage(ConsumerRecord("credit.evaluation.completed.v1", 0, 1, event.eventId.toString(), payload))
        assertThat(count("processed_credit_evaluation_event")).isEqualTo(1)
        assertThat(count("consumed_credit_evaluation_effect")).isEqualTo(1)
    }

    @Test
    // @spec:AC-059
    fun `AC-059 effect failure rolls back the processing marker in the same PostgreSQL transaction`() {
        val failingListener = CreditEvaluationKafkaListener(
            objectMapper,
            processedEventStore,
            providerOf(CreditEvaluationEventEffect { error("effect failed") }),
        )
        assertThatThrownBy { failingListener.onPayload(objectMapper.writeValueAsString(event())) }
            .hasMessageContaining("effect failed")
        assertThat(count("processed_credit_evaluation_event")).isZero()
    }

    @Test
    // @spec:AC-060
    fun `AC-060 event from producer to Kafka listener preserves the public contract without sensitive data`() {
        var consumed: CreditEvaluationCompleted? = null
        val capturingListener = CreditEvaluationKafkaListener(
            objectMapper,
            processedEventStore,
            providerOf(CreditEvaluationEventEffect { consumed = it }),
        )
        val published = mutableListOf<Pair<String, String>>()
        val event = event()
        CreditEvaluationEventProducer(BrokerPublisher { _, key, payload -> published += key to payload }, objectMapper)
            .publish(event)
        val (key, payload) = published.single()
        capturingListener.onMessage(ConsumerRecord("credit.evaluation.completed.v1", 0, 0, key, payload))
        assertThat(consumed).isEqualTo(event)
        assertThat(payload).contains(
            "eventVersion", "evaluationId", "decision", "approvedAmount", "ruleVersion", "evaluatedAt", "correlationId",
        )
        assertThat(payload.lowercase()).doesNotContain("cpf", "token", "password", "exception", "stacktrace")
    }

    private fun count(table: String): Long =
        jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)!!

    private fun providerOf(effect: CreditEvaluationEventEffect): ObjectProvider<CreditEvaluationEventEffect> =
        StaticListableBeanFactory().apply { addBean("effect", effect) }
            .getBeanProvider(CreditEvaluationEventEffect::class.java)

    private fun event() = CreditEvaluationCompleted(
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), 1,
        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "APPROVED", BigDecimal("1200.50"), "2026.08",
        Instant.parse("2026-08-15T10:00:00Z"), UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
    )

    private companion object {
        @Container val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
