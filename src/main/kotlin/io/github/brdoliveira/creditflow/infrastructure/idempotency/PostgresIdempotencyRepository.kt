package io.github.brdoliveira.creditflow.infrastructure.idempotency

import io.github.brdoliveira.creditflow.application.port.IdempotencyKeyConflictException
import io.github.brdoliveira.creditflow.application.port.IdempotencyExecution
import io.github.brdoliveira.creditflow.application.port.IdempotencyRepository
import io.github.brdoliveira.creditflow.application.port.InvalidIdempotencyKeyException
import io.github.brdoliveira.creditflow.application.port.MissingIdempotencyKeyException
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class PostgresIdempotencyRepository(
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val requestHasher: CanonicalRequestHasher = CanonicalRequestHasher(),
    private val retention: Duration = Duration.ofHours(24),
    private val now: () -> Instant = Instant::now,
) : IdempotencyRepository {
    override fun executeWithOutcome(
        idempotencyKey: String?,
        requestBody: String,
        operation: () -> String,
    ): IdempotencyExecution {
        val key = parseKey(idempotencyKey)
        val requestHash = requestHasher.hash(requestBody)

        val execution = transactionTemplate.execute<IdempotencyExecution?> {
            val timestamp = now()
            entityManager.createNativeQuery("delete from credit_idempotency where idempotency_key = :key and expires_at <= :now")
                .setParameter("key", key)
                .setParameter("now", timestamp)
                .executeUpdate()
            entityManager.createNativeQuery(
                """insert into credit_idempotency (idempotency_key, request_hash, expires_at)
                   values (:key, :hash, :expiresAt) on conflict (idempotency_key) do nothing""",
            )
                .setParameter("key", key)
                .setParameter("hash", requestHash)
                .setParameter("expiresAt", timestamp.plus(retention))
                .executeUpdate()

            @Suppress("UNCHECKED_CAST")
            val row = entityManager.createNativeQuery(
                "select request_hash, response_body from credit_idempotency where idempotency_key = :key for update",
            ).setParameter("key", key).singleResult as Array<Any?>

            if (row[0] != requestHash) return@execute null
            (row[1] as String?)?.let { return@execute IdempotencyExecution(it, replayed = true) }

            val response = operation()
            entityManager.createNativeQuery(
                """update credit_idempotency
                   set response_body = :response, completed_at = :completedAt
                   where idempotency_key = :key""",
            )
                .setParameter("response", response)
                .setParameter("completedAt", now())
                .setParameter("key", key)
                .executeUpdate()
            IdempotencyExecution(response, replayed = false)
        }
        return execution ?: throw IdempotencyKeyConflictException()
    }

    private fun parseKey(value: String?): UUID {
        if (value.isNullOrBlank()) throw MissingIdempotencyKeyException()
        return try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw InvalidIdempotencyKeyException()
        }
    }
}
