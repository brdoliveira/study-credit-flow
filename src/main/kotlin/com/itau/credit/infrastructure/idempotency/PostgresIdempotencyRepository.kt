package com.itau.credit.infrastructure.idempotency

import com.itau.credit.application.port.IdempotencyKeyConflictException
import com.itau.credit.application.port.IdempotencyRepository
import com.itau.credit.application.port.InvalidIdempotencyKeyException
import com.itau.credit.application.port.MissingIdempotencyKeyException
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
class PostgresIdempotencyRepository(
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val requestHasher: CanonicalRequestHasher = CanonicalRequestHasher(),
    private val retention: Duration = Duration.ofHours(24),
    private val now: () -> Instant = Instant::now,
) : IdempotencyRepository {
    override fun execute(idempotencyKey: String?, requestBody: String, operation: () -> String): String {
        val key = parseKey(idempotencyKey)
        val requestHash = requestHasher.hash(requestBody)

        return requireNotNull(transactionTemplate.execute {
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
                "select request_hash, response_body::text from credit_idempotency where idempotency_key = :key for update",
            ).setParameter("key", key).singleResult as Array<Any?>

            if (row[0] != requestHash) throw IdempotencyKeyConflictException()
            (row[1] as String?)?.let { return@execute it }

            val response = operation()
            entityManager.createNativeQuery(
                """update credit_idempotency
                   set response_body = cast(:response as jsonb), completed_at = :completedAt
                   where idempotency_key = :key""",
            )
                .setParameter("response", response)
                .setParameter("completedAt", now())
                .setParameter("key", key)
                .executeUpdate()
            response
        })
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
