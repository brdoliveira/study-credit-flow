package io.github.brdoliveira.creditflow.infrastructure.messaging

import org.springframework.kafka.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Aguarda a confirmação do broker antes de devolver o controle à outbox. */
class KafkaBrokerPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val acknowledgementTimeout: Duration = Duration.ofSeconds(10),
) : BrokerPublisher {
    /** Publica a mensagem e converte falhas recuperáveis em erro transitório. */
    override fun publish(topic: String, key: String, payload: String) {
        try {
            kafkaTemplate.send(topic, key, payload).get(acknowledgementTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            transient(error)
        } catch (error: ExecutionException) {
            transient(error)
        } catch (error: TimeoutException) {
            transient(error)
        } catch (error: KafkaException) {
            transient(error)
        }
    }

    private fun transient(error: Throwable): Nothing =
        throw TransientBrokerException("Kafka broker acknowledgement failed", error)
}
