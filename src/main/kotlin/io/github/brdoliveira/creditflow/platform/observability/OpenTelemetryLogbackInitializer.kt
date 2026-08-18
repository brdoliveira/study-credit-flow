package io.github.brdoliveira.creditflow.platform.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component

/** Conecta o appender Logback ao SDK OpenTelemetry gerenciado pelo Spring Boot. */
@Component
class OpenTelemetryLogbackInitializer(
    private val openTelemetry: OpenTelemetry,
) : InitializingBean {
    /** Instala o SDK OpenTelemetry no appender depois que o contexto Spring estiver pronto. */
    override fun afterPropertiesSet() = OpenTelemetryAppender.install(openTelemetry)
}
