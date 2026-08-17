package io.github.brdoliveira.creditflow.evaluation.infrastructure.observability

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicLong

/** Atualiza gauges operacionais da outbox sem executar SQL durante cada scrape. */
class OutboxBacklogMetrics(
    registry: MeterRegistry,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val pending = AtomicLong()
    private val failed = AtomicLong()
    private val oldestPendingAgeSeconds = AtomicLong()

    init {
        Gauge.builder("credit.outbox.backlog", pending) { value -> value.get().toDouble() }
            .description("Events waiting or being processed by the outbox")
            .register(registry)
        Gauge.builder("credit.outbox.failed", failed) { value -> value.get().toDouble() }
            .description("Events in terminal outbox failure")
            .register(registry)
        Gauge.builder("credit.outbox.oldest.pending.age.seconds", oldestPendingAgeSeconds) { value -> value.get().toDouble() }
            .description("Age in seconds of the oldest pending outbox event")
            .register(registry)
    }

    /** Consulta um snapshot único do banco e atualiza os gauges em memória. */
    @Scheduled(
        fixedDelayString = "\${credit.outbox.metrics-refresh-delay:PT10S}",
        initialDelayString = "\${credit.outbox.metrics-initial-delay:PT1S}",
    )
    fun refresh() {
        runCatching {
            jdbcTemplate.queryForObject(OUTBOX_SNAPSHOT) { resultSet, _ ->
                Snapshot(
                    pending = resultSet.getLong("pending_count"),
                    failed = resultSet.getLong("failed_count"),
                    oldestPendingAgeSeconds = resultSet.getLong("oldest_pending_age_seconds"),
                )
            }
        }.onSuccess { snapshot ->
            pending.set(snapshot.pending)
            failed.set(snapshot.failed)
            oldestPendingAgeSeconds.set(snapshot.oldestPendingAgeSeconds)
        }.onFailure { error ->
            logger.warn("Outbox backlog metrics refresh failed: failureType={}", error.javaClass.simpleName)
        }
    }

    private data class Snapshot(
        val pending: Long,
        val failed: Long,
        val oldestPendingAgeSeconds: Long,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(OutboxBacklogMetrics::class.java)
        val OUTBOX_SNAPSHOT = """
            select
                count(*) filter (where status in ('PENDING', 'PROCESSING')) as pending_count,
                count(*) filter (where status = 'FAILED') as failed_count,
                coalesce(
                    extract(epoch from current_timestamp - min(created_at)
                        filter (where status in ('PENDING', 'PROCESSING'))),
                    0
                )::bigint as oldest_pending_age_seconds
            from credit_outbox
        """.trimIndent()
    }
}
