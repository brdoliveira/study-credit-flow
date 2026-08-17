package io.github.brdoliveira.creditflow.platform.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.logging.logback.StructuredLogEncoder
import org.springframework.core.env.Environment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StructuredLoggingIT {
    @Test
    // @spec:AC-100
    fun `AC-100 console emits Logstash JSON with service identity and available MDC`() {
        val loggingConfiguration = projectFile("src/main/resources/logback-spring.xml")
        assertTrue(loggingConfiguration.contains("org.springframework.boot.logging.logback.StructuredLogEncoder"))
        assertTrue(loggingConfiguration.contains("source=\"logging.structured.format.console\""))

        val json = encodedEvent()

        assertNotNull(json["@timestamp"])
        assertEquals("INFO", json["level"].asText())
        assertEquals("credit-flow.structured.logging", json["logger_name"].asText())
        assertEquals("operational event", json["message"].asText())
        assertEquals("credit-flow", json["service.name"].asText())
        assertEquals("2026.08", json["service.version"].asText())
        assertEquals("test", json["service.environment"].asText())
        assertEquals("correlation-123", json["correlationId"].asText())
        assertEquals("trace-123", json["traceId"].asText())
        assertEquals("span-123", json["spanId"].asText())
    }

    @Test
    // @spec:AC-101
    fun `AC-101 structured logging allows only operational MDC and keeps nominal operations below INFO`() {
        val observabilityConfiguration = projectFile("src/main/resources/application-observability.yml")
        assertTrue(observabilityConfiguration.contains("console: logstash"))
        assertTrue(observabilityConfiguration.contains("exclude:"))

        val serialized = encodedEvent(sensitiveMdcEvent()).toString()

        assertFalse(serialized.contains("123.456.789-09"))
        assertFalse(serialized.contains("token-secret"))
        assertFalse(serialized.contains("5000.00"))
        assertFalse(serialized.contains("request-body"))
        assertFalse(serialized.contains("event-payload"))
        assertEquals("DEBUG", event(Level.DEBUG).level.levelStr)
    }

    private fun encodedEvent(event: LoggingEvent = event()) = JsonMapper.builder().build().readTree(encode(event))

    private fun encode(event: LoggingEvent): String {
        val loggerContext = LoggerContext()
        val encoder = StructuredLogEncoder().apply {
            context = loggerContext.apply { putObject(Environment::class.java.name, environment()) }
            setFormat("logstash")
            start()
        }
        return String(encoder.encode(event), StandardCharsets.UTF_8).also { encoder.stop() }
    }

    private fun environment(): StandardEnvironment = StandardEnvironment().apply {
        propertySources.addFirst(MapPropertySource("test", mapOf(
            "spring.application.name" to "credit-flow",
            "APP_VERSION" to "2026.08",
            "APP_ENVIRONMENT" to "test",
        )))
        YamlPropertySourceLoader().load("observability", ClassPathResource("application-observability.yml"))
            .reversed()
            .forEach(propertySources::addFirst)
    }

    private fun event(
        level: Level = Level.INFO,
        mdc: Map<String, String> = operationalMdc(),
    ): LoggingEvent = LoggingEvent().apply {
        loggerName = "credit-flow.structured.logging"
        this.level = level
        message = "operational event"
        timeStamp = 1_770_000_000_000
        mdcPropertyMap = mdc
    }

    private fun operationalMdc(): Map<String, String> = mapOf(
        "correlationId" to "correlation-123",
        "traceId" to "trace-123",
        "spanId" to "span-123",
    )

    private fun sensitiveMdcEvent(): LoggingEvent = event(
        mdc = operationalMdc() + mapOf(
            "cpf" to "123.456.789-09",
            "token" to "token-secret",
            "amount" to "5000.00",
            "requestBody" to "request-body",
            "payload" to "event-payload",
        ),
    )

    private fun projectFile(path: String): String = java.nio.file.Path.of(path).readText()
}
