package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

/** Indica uma falha transitória na publicação de uma mensagem. */
class TransientBrokerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
