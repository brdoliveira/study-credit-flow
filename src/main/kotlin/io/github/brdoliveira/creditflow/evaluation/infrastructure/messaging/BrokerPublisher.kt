package io.github.brdoliveira.creditflow.evaluation.infrastructure.messaging

/** Abstrai a publicação de mensagens em um broker. */
fun interface BrokerPublisher {
    /** Publica a mensagem ou lança [TransientBrokerException] quando uma nova tentativa é possível. */
    fun publish(topic: String, key: String, payload: String)
}
