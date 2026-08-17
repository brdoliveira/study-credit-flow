package io.github.brdoliveira.creditflow.evaluation.application.port

/** Indica que a chave de idempotência não é um UUID válido. */
class InvalidIdempotencyKeyException : IllegalArgumentException("Idempotency-Key must be a UUID")
