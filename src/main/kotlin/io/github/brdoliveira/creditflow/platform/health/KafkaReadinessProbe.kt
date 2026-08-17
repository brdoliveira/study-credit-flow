package io.github.brdoliveira.creditflow.platform.health

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Verifica o Kafka com timeout total limitado, inclusive no fechamento do cliente. */
class KafkaReadinessProbe(
    private val bootstrapServers: String,
    private val timeout: Duration = Duration.ofSeconds(2),
) : RequiredDependencyProbe {
    /** Consulta o identificador do cluster sem bloquear a resposta de readiness no fechamento. */
    override fun isAvailable(): Boolean {
        val timeoutMillis = timeout.toMillis().coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val client = AdminClient.create(
            mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG to timeoutMillis,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG to timeoutMillis,
            ),
        )
        return try {
            client.describeCluster().clusterId().get(timeoutMillis.toLong(), TimeUnit.MILLISECONDS).isNotBlank()
        } finally {
            client.close(Duration.ZERO)
        }
    }
}
