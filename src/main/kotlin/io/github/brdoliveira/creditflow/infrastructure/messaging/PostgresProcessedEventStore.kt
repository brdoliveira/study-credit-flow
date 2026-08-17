package io.github.brdoliveira.creditflow.infrastructure.messaging

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class PostgresProcessedEventStore(
    private val jdbcTemplate: JdbcTemplate,
    private val transactions: TransactionTemplate,
) : ProcessedEventStore {
    override fun processOnce(eventId: UUID, effect: () -> Unit): Boolean = transactions.execute {
        val inserted = jdbcTemplate.update(
            """INSERT INTO processed_credit_evaluation_event (event_id)
               VALUES (?) ON CONFLICT (event_id) DO NOTHING""",
            eventId,
        )
        if (inserted == 0) false else {
            effect()
            true
        }
    } ?: false
}
