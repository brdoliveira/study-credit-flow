package io.github.brdoliveira.creditflow.evaluation.application.port

/** Indica ausência do cabeçalho obrigatório de idempotência. */
class MissingIdempotencyKeyException : IllegalArgumentException("Idempotency-Key is required")
