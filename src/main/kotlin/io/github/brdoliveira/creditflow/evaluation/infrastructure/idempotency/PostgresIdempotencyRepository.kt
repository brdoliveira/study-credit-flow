package io.github.brdoliveira.creditflow.evaluation.infrastructure.idempotency

import io.github.brdoliveira.creditflow.evaluation.application.port.*
import io.github.brdoliveira.creditflow.infrastructure.idempotency.CanonicalRequestHasher
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Coordena reserva e replay idempotente na tabela PostgreSQL por 24 horas. */
@Component
class PostgresIdempotencyRepository(private val entityManager: EntityManager, private val transactionTemplate: TransactionTemplate, private val requestHasher: CanonicalRequestHasher = CanonicalRequestHasher(), private val now: () -> Instant = Instant::now) : IdempotencyRepository {
    /** Executa uma operação uma vez por chave e devolve o resultado armazenado em replays. */
    override fun execute(key: String?, requestBody: String, operation: () -> String): IdempotencyExecution {
        val parsed = key?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw IllegalArgumentException("Idempotency-Key must be a UUID")
        return transactionTemplate.execute {
            val timestamp = now()
            entityManager.createNativeQuery("delete from credit_idempotency where idempotency_key = :key and expires_at <= :now").setParameter("key", parsed).setParameter("now", timestamp).executeUpdate()
            entityManager.createNativeQuery("insert into credit_idempotency (idempotency_key, request_hash, expires_at) values (:key, :hash, :expiresAt) on conflict (idempotency_key) do nothing").setParameter("key", parsed).setParameter("hash", requestHasher.hash(requestBody)).setParameter("expiresAt", timestamp.plus(Duration.ofHours(24))).executeUpdate()
            @Suppress("UNCHECKED_CAST") val row = entityManager.createNativeQuery("select request_hash, response_body from credit_idempotency where idempotency_key = :key for update").setParameter("key", parsed).singleResult as Array<Any?>
            if (row[0] != requestHasher.hash(requestBody)) throw IllegalArgumentException("Idempotency-Key was reused with a different request")
            (row[1] as String?)?.let { return@execute IdempotencyExecution(it, true) }
            val response = operation(); entityManager.createNativeQuery("update credit_idempotency set response_body = :response, completed_at = :completedAt where idempotency_key = :key").setParameter("response", response).setParameter("completedAt", now()).setParameter("key", parsed).executeUpdate(); IdempotencyExecution(response, false)
        } ?: error("Idempotency transaction did not complete")
    }
}
