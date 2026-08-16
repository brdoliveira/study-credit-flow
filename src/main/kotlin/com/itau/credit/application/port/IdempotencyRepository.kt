package com.itau.credit.application.port

/**
 * Executes an operation once for a request key and returns its persisted JSON response.
 * Implementations must make the reservation, operation and response persistence atomic.
 */
interface IdempotencyRepository {
    fun execute(
        idempotencyKey: String?,
        requestBody: String,
        operation: () -> String,
    ): String = executeWithOutcome(idempotencyKey, requestBody, operation).responseBody

    fun executeWithOutcome(
        idempotencyKey: String?,
        requestBody: String,
        operation: () -> String,
    ): IdempotencyExecution
}

data class IdempotencyExecution(
    val responseBody: String,
    val replayed: Boolean,
)

class MissingIdempotencyKeyException : IllegalArgumentException("Idempotency-Key is required")

class InvalidIdempotencyKeyException : IllegalArgumentException("Idempotency-Key must be a UUID")

class IdempotencyKeyConflictException : IllegalStateException("Idempotency-Key was already used with a different request")
