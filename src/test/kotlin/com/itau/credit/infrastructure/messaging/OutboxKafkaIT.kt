package com.itau.credit.infrastructure.messaging

import com.itau.credit.application.event.CreditEvaluationCompleted
import com.itau.credit.infrastructure.outbox.OutboxPublisher
import com.itau.credit.infrastructure.outbox.PostgresOutboxStore
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

private const val OUTBOX_TOPIC = "credit.evaluation.completed.v1"

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = [OUTBOX_TOPIC], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ExtendWith(SpringExtension::class)
class OutboxKafkaIT @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionManager: PlatformTransactionManager,
    private val embeddedKafka: EmbeddedKafkaBroker,
) {
    private val objectMapper = ObjectMapper()
    private val kafkaTemplate = KafkaTemplate<String, String>(DefaultKafkaProducerFactory(producerProperties()))
    private val store = PostgresOutboxStore(jdbcTemplate, objectMapper)

    @AfterEach
    fun cleanDatabase() {
        jdbcTemplate.update("delete from credit_outbox")
        jdbcTemplate.update("delete from credit_evaluation")
    }

    @Test
    // @spec:AC-056
    fun `AC-056 evaluation and outbox commit atomically in real PostgreSQL`() {
        val committedId = UUID.randomUUID()
        newTransaction().executeWithoutResult { insertEvaluation(committedId) }
        assertThat(count("credit_evaluation", committedId)).isEqualTo(1)
        assertThat(count("credit_outbox", committedId)).isEqualTo(1)
        val rolledBackId = UUID.randomUUID()
        newTransaction().executeWithoutResult { status -> insertEvaluation(rolledBackId); status.setRollbackOnly() }
        assertThat(count("credit_evaluation", rolledBackId)).isZero()
        assertThat(count("credit_outbox", rolledBackId)).isZero()
    }

    @Test
    // @spec:AC-057
    fun `AC-057 publisher waits for Kafka acknowledgement before confirming the Outbox`() {
        val event = event()
        insertOutbox(event)
        val publisher = OutboxPublisher(
            store, CreditEvaluationEventProducer(KafkaBrokerPublisher(kafkaTemplate), objectMapper, OUTBOX_TOPIC),
        )
        consumer().use { consumer ->
            publisher.publishPending()
            val record = KafkaTestUtils.getRecords(consumer).records(OUTBOX_TOPIC)
                .single { it.key() == event.eventId.toString() }
            assertThat(record.value()).contains(event.eventId.toString())
        }
        assertThat(statusOf(event.eventId)).isEqualTo("PUBLISHED")
    }

    @Test
    // @spec:AC-058
    fun `AC-058 transient broker failure persists sanitized retry and publishes after recovery`() {
        val event = event()
        insertOutbox(event)
        val now = Instant.parse("2026-08-16T12:00:00Z")
        jdbcTemplate.update("update credit_outbox set next_attempt_at = ? where event_id = ?", Timestamp.from(now), event.eventId)
        var available = false
        val producer = CreditEvaluationEventProducer(
            BrokerPublisher { _, _, _ -> if (!available) throw TransientBrokerException("token=secret\nnot persisted") },
            objectMapper, OUTBOX_TOPIC,
        )
        OutboxPublisher(store, producer, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(1), Duration.ofSeconds(2))
            .publishPending()
        val retry = jdbcTemplate.queryForMap(
            "select status, attempts, next_attempt_at, last_error from credit_outbox where event_id = ?", event.eventId,
        )
        assertThat(retry["status"]).isEqualTo("PENDING")
        assertThat((retry["attempts"] as Number).toInt()).isEqualTo(1)
        assertThat(retry["last_error"].toString()).doesNotContain("secret", "token=")
        available = true
        OutboxPublisher(store, producer, Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC)).publishPending()
        assertThat(statusOf(event.eventId)).isEqualTo("PUBLISHED")
    }

    @Test
    // @spec:AC-060
    fun `AC-060 event crossing Outbox and Kafka preserves contract without private data`() {
        val event = event()
        insertOutbox(event)
        val publisher = OutboxPublisher(
            store, CreditEvaluationEventProducer(KafkaBrokerPublisher(kafkaTemplate), objectMapper, OUTBOX_TOPIC),
        )
        consumer().use { consumer ->
            publisher.publishPending()
            val payload = KafkaTestUtils.getRecords(consumer).records(OUTBOX_TOPIC)
                .single { it.key() == event.eventId.toString() }.value()
            assertThat(payload).contains(
                "eventVersion", "evaluationId", "decision", "approvedAmount", "ruleVersion", "evaluatedAt", "correlationId",
            )
            assertThat(payload).doesNotContain("cpf", "password", "token", "exception")
        }
    }

    private fun insertEvaluation(evaluationId: UUID) {
        jdbcTemplate.update(
            """insert into credit_evaluation (evaluation_id, cpf_masked, decision, approved_amount, rule_version, rule_results, evaluated_at, duration_millis, correlation_id)
               values (?, '***.***.***-09', 'APPROVED', 1200.50, '2026.08', '[]'::jsonb, ?, 12, ?)""",
            evaluationId, Timestamp.from(Instant.parse("2026-08-16T12:00:00Z")), UUID.randomUUID().toString(),
        )
    }

    private fun insertOutbox(event: CreditEvaluationCompleted) {
        insertEvaluation(event.evaluationId)
        jdbcTemplate.update(
            """update credit_outbox set event_id = ?, payload = ?::jsonb, next_attempt_at = current_timestamp
               where evaluation_id = ?""",
            event.eventId, objectMapper.writeValueAsString(event), event.evaluationId,
        )
    }

    private fun consumer(): KafkaConsumer<String, String> {
        val properties = KafkaTestUtils.consumerProps("outbox-${UUID.randomUUID()}", "true", embeddedKafka)
        properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        return KafkaConsumer<String, String>(properties).also { it.subscribe(listOf(OUTBOX_TOPIC)) }
    }

    private fun producerProperties(): Map<String, Any> = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafka.brokersAsString,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.ACKS_CONFIG to "all",
    )

    private fun newTransaction() = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }
    private fun count(table: String, id: UUID): Int = jdbcTemplate.queryForObject(
        "select count(*) from $table where evaluation_id = ?", Int::class.java, id,
    ) ?: 0
    private fun statusOf(eventId: UUID): String = jdbcTemplate.queryForObject(
        "select status from credit_outbox where event_id = ?", String::class.java, eventId,
    )!!
    private fun event() = CreditEvaluationCompleted(
        UUID.randomUUID(), 1, UUID.randomUUID(), "APPROVED", BigDecimal("1200.50"), "2026.08",
        Instant.parse("2026-08-16T12:00:00Z"), UUID.randomUUID(),
    )

    private companion object {
        @Container val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@Suppress("unused")
private class OutboxKafkaTestApplication
