package io.github.brdoliveira.creditflow.evaluation.infrastructure.idempotency

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationResult
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyExecution
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyKeyConflictException
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyRepository
import io.github.brdoliveira.creditflow.evaluation.application.port.InvalidIdempotencyKeyException
import io.github.brdoliveira.creditflow.evaluation.application.port.MissingIdempotencyKeyException
import io.github.brdoliveira.creditflow.evaluation.infrastructure.idempotency.CanonicalRequestHasher
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Coordena reserva e replay idempotente na tabela PostgreSQL por 24 horas. */
@Component
class PostgresIdempotencyRepository(
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    private val requestHasher: CanonicalRequestHasher = CanonicalRequestHasher(),
    private val retention: Duration = Duration.ofHours(24),
    private val now: () -> Instant = Instant::now,
) : IdempotencyRepository {
    /** Executa uma operação uma vez por chave e devolve o resultado armazenado em replays. */
    override fun execute(
        key: String?,
        requestBody: String,
        operation: () -> CreateCreditEvaluationResult,
    ): IdempotencyExecution {
        val parsed = parseKey(key)
        val requestHash = requestHasher.hash(requestBody)
        val execution = transactionTemplate.execute<IdempotencyExecution?> {
            val timestamp = now()
            entityManager.createNativeQuery(DELETE_EXPIRED)
                .setParameter("key", parsed)
                .setParameter("now", timestamp)
                .executeUpdate()
            entityManager.createNativeQuery(RESERVE_KEY)
                .setParameter("key", parsed)
                .setParameter("hash", requestHash)
                .setParameter("expiresAt", timestamp.plus(retention))
                .executeUpdate()
            @Suppress("UNCHECKED_CAST")
            val row = entityManager.createNativeQuery(LOCK_KEY)
                .setParameter("key", parsed)
                .singleResult as Array<Any?>
            if (row[0] != requestHash) return@execute null
            (row[1] as String?)?.let { body ->
                return@execute IdempotencyExecution(
                    objectMapper.readValue(body, CreateCreditEvaluationResult::class.java),
                    replayed = true,
                )
            }
            val result = operation()
            entityManager.createNativeQuery("update credit_idempotency set response_body = :response, completed_at = :completedAt where idempotency_key = :key")
                .setParameter("response", objectMapper.writeValueAsString(result))
                .setParameter("completedAt", now())
                .setParameter("key", parsed)
                .executeUpdate()
            IdempotencyExecution(result, replayed = false)
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

    private companion object {
        const val DELETE_EXPIRED =
            "delete from credit_idempotency where idempotency_key = :key and expires_at <= :now"
        const val RESERVE_KEY = """
            insert into credit_idempotency (idempotency_key, request_hash, expires_at)
            values (:key, :hash, :expiresAt)
            on conflict (idempotency_key) do nothing
        """
        const val LOCK_KEY = """
            select request_hash, response_body from credit_idempotency
            where idempotency_key = :key for update
        """
    }
}
