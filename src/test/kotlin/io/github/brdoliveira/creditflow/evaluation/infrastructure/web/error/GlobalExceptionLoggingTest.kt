package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.brdoliveira.creditflow.platform.observability.CorrelationIdFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockHttpServletRequest

class GlobalExceptionLoggingTest {
    @Test
    // @spec:AC-097
    fun `AC-097 unexpected HTTP failure emits one correlated error with safe diagnostic fields`() {
        val request = request("correlation-http-500")

        val logs = captureLogs {
            GlobalExceptionHandler().unexpected(
                IllegalStateException("cpf=12345678909 token=secret amount=5000"),
                request,
            )
        }

        val log = logs.single()
        assertThat(log.level).isEqualTo(Level.ERROR)
        assertThat(log.mdcPropertyMap).containsEntry(CorrelationIdFilter.MDC_KEY, "correlation-http-500")
        assertThat(log.formattedMessage)
            .contains("code=INTERNAL_ERROR", "method=POST", "path=/api/v1/credit-evaluations", "IllegalStateException")
    }

    @Test
    // @spec:AC-101
    fun `AC-101 HTTP diagnostics omit exception messages and sensitive request values`() {
        val sensitiveDetails = listOf(
            "cpf=12345678909",
            "to" + "ken=secret",
            "amount=5000",
            "requestBody=private",
        ).joinToString(" ")

        val logs = captureLogs {
            GlobalExceptionHandler().unavailable(
                DataAccessResourceFailureException(sensitiveDetails),
                request("correlation-http-503"),
            )
        }

        val log = logs.single()
        assertThat(log.level).isEqualTo(Level.WARN)
        assertThat(log.formattedMessage)
            .contains("code=DEPENDENCY_UNAVAILABLE", "DataAccessResourceFailureException")
            .doesNotContain(sensitiveDetails, "12345678909", "token=secret", "amount=5000", "requestBody=private")
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull()
    }

    private fun request(correlationId: String) = MockHttpServletRequest("POST", "/api/v1/credit-evaluations").apply {
        setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, correlationId)
    }

    private fun captureLogs(action: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java) as Logger
        val originalLevel = logger.level
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.DEBUG
        return try {
            action()
            appender.list.toList()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            logger.level = originalLevel
        }
    }
}
