package io.github.brdoliveira.creditflow.evaluation.infrastructure.observability

import io.github.brdoliveira.creditflow.infrastructure.health.DependencyReadinessIndicator
import io.github.brdoliveira.creditflow.infrastructure.health.RequiredDependencyProbe
import io.github.brdoliveira.creditflow.infrastructure.observability.CorrelationIdFilter
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error.GlobalExceptionHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ObservabilityTest {
    @Test
    // @spec:AC-037
    fun `AC-037 normalized correlation follows request response logs and downstream processing`() {
        val request = MockHttpServletRequest().apply { addHeader(CorrelationIdFilter.HEADER_NAME, "operation-123") }
        val response = MockHttpServletResponse()
        var downstreamHeader: String? = null
        var logCorrelation: String? = null

        CorrelationIdFilter().doFilter(request, response) { correlatedRequest, _ ->
            downstreamHeader = (correlatedRequest as jakarta.servlet.http.HttpServletRequest).getHeader(CorrelationIdFilter.HEADER_NAME)
            logCorrelation = MDC.get(CorrelationIdFilter.MDC_KEY)
        }

        assertEquals("operation-123", response.getHeader(CorrelationIdFilter.HEADER_NAME))
        assertEquals("operation-123", downstreamHeader)
        assertEquals("operation-123", logCorrelation)
        assertEquals(null, MDC.get(CorrelationIdFilter.MDC_KEY))
    }

    @Test
    // @spec:AC-038
    fun `AC-038 essential bounded-cardinality counters duration and throughput are recorded`() {
        val registry = SimpleMeterRegistry()
        val metrics = CreditMetrics(registry)

        metrics.recordEvaluation("APPROVED", Duration.ofMillis(25))
        metrics.recordEvaluation("REJECTED", Duration.ofMillis(10))
        metrics.recordTechnicalError("DEPENDENCY")
        metrics.recordRuleFailure("MINIMUM_SCORE")

        assertEquals(1.0, registry.counter("credit.evaluations", "decision", "APPROVED").count())
        assertEquals(1.0, registry.counter("credit.evaluations", "decision", "REJECTED").count())
        assertEquals(2, registry.timer("credit.evaluation.duration").count())
        assertEquals(1.0, registry.counter("credit.evaluation.errors", "type", "DEPENDENCY").count())
        assertEquals(1.0, registry.counter("credit.rule.failures", "rule", "MINIMUM_SCORE").count())
    }

    @Test
    // @spec:AC-039
    fun `AC-039 readiness goes down for a required dependency while liveness remains independent`() {
        val ready = DependencyReadinessIndicator(mapOf("postgres" to RequiredDependencyProbe { true })).health()
        val unavailable = DependencyReadinessIndicator(
            mapOf("postgres" to RequiredDependencyProbe { false }, "kafka" to RequiredDependencyProbe { true })
        ).health()

        assertEquals("UP", ready.status.code)
        assertEquals("DOWN", unavailable.status.code)
        assertEquals(listOf("postgres"), unavailable.details["unavailableDependencies"])
    }

    @Test
    // @spec:AC-040
    fun `AC-040 technical errors have stable status code and correlation without stack trace`() {
        val request = MockHttpServletRequest("GET", "/api/v1/credit-evaluations").apply {
            setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "operation-500")
        }
        val handler = GlobalExceptionHandler()

        val internal = handler.unexpected(request)
        val unavailable = handler.unavailable(request)

        assertEquals(500, internal.statusCode.value())
        assertEquals("INTERNAL_ERROR", internal.body!!.code)
        assertEquals(503, unavailable.statusCode.value())
        assertEquals("DEPENDENCY_UNAVAILABLE", unavailable.body!!.code)
        assertEquals("operation-500", internal.body!!.correlationId)
        assertFalse(internal.body!!.toString().contains("secret details"))
        assertNotNull(internal.body!!.timestamp)
    }
}
