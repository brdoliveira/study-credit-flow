package com.itau.credit.infrastructure.outbox

import com.itau.credit.application.event.CreditEvaluationCompleted
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Claims rows before publishing so concurrent schedulers cannot publish the same lease. */
class PostgresOutboxStore(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val leaseDuration: Duration = Duration.ofMinutes(1),
) : OutboxStore {
    override fun pending(now: Instant, limit: Int): List<PendingOutboxEvent> {
        require(limit > 0) { "limit must be positive" }
        val leaseUntil = now.plus(leaseDuration)
        return jdbcTemplate.query(
            CLAIM_PENDING,
            { resultSet, _ ->
                PendingOutboxEvent(
                    UUID.fromString(resultSet.getString("event_id")),
                    objectMapper.readValue(resultSet.getString("payload"), CreditEvaluationCompleted::class.java),
                    resultSet.getInt("attempts"),
                )
            },
            Timestamp.from(now), Timestamp.from(now), limit, Timestamp.from(leaseUntil),
        )
    }

    override fun markPublished(eventId: UUID, publishedAt: Instant) {
        jdbcTemplate.update(
            """update credit_outbox set status = 'PUBLISHED', published_at = ?, last_error = null
               where event_id = ? and status = 'PROCESSING'""",
            Timestamp.from(publishedAt), eventId,
        )
    }

    override fun reschedule(eventId: UUID, attempts: Int, nextAttemptAt: Instant, reason: String) {
        jdbcTemplate.update(
            """update credit_outbox set status = 'PENDING', attempts = ?, next_attempt_at = ?, last_error = ?
               where event_id = ? and status = 'PROCESSING'""",
            attempts, Timestamp.from(nextAttemptAt), sanitize(reason), eventId,
        )
    }

    private fun sanitize(reason: String): String = reason.replace(Regex("[\\r\\n\\t]+"), " ").take(MAX_ERROR_LENGTH)

    private companion object {
        const val MAX_ERROR_LENGTH = 512
        val CLAIM_PENDING = """
            with candidates as (
                select event_id from credit_outbox
                where (status = 'PENDING' or (status = 'PROCESSING' and next_attempt_at <= ?))
                  and next_attempt_at <= ?
                order by created_at
                for update skip locked
                limit ?
            )
            update credit_outbox outbox set status = 'PROCESSING', next_attempt_at = ?
            from candidates where outbox.event_id = candidates.event_id
            returning outbox.event_id, outbox.payload, outbox.attempts
        """.trimIndent()
    }
}
