package io.github.brdoliveira.creditflow.evaluation.application.port

/** Indica reutilização da chave com conteúdo diferente. */
class IdempotencyKeyConflictException : IllegalStateException("Idempotency-Key was already used with a different request")
