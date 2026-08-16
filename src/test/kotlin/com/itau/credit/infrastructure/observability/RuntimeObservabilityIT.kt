package com.itau.credit.infrastructure.observability

import com.itau.credit.infrastructure.health.DependencyReadinessIndicator
import com.itau.credit.infrastructure.health.RequiredDependencyProbe
import com.itau.credit.infrastructure.web.CreditEvaluationResponse
import com.itau.credit.infrastructure.web.GlobalExceptionHandler
import com.itau.credit.infrastructure.web.IdempotentCreditEvaluationResponse
import com.itau.credit.infrastructure.web.RuleResponse
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.mock.web.MockHttpServletRequest
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeObservabilityIT {
    @Test
    // @spec:AC-061
    fun `AC-061 runtime evaluation records decision duration and failed rules once`() {
        val registry = SimpleMeterRegistry()
        val observer = ObservedCreditEvaluationService(CreditMetrics(registry))

        observer.observe { IdempotentCreditEvaluationResponse(response(), replayed = false) }
        observer.observe { IdempotentCreditEvaluationResponse(response(), replayed = true) }

        assertEquals(1.0, registry.counter("credit.evaluations", "decision", "REJECTED").count())
        assertEquals(1, registry.timer("credit.evaluation.duration").count())
        assertEquals(1.0, registry.counter("credit.rule.failures", "rule", "MINIMUM_SCORE").count())
    }

    @Test
    // @spec:AC-062
    fun `AC-062 global handler records one bounded technical metric per response`() {
        val registry = SimpleMeterRegistry()
        val handler = GlobalExceptionHandler(CreditMetrics(registry))
        val request = MockHttpServletRequest("GET", "/api/v1/credit-evaluations").apply {
            setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "runtime-correlation")
        }

        val internal = handler.unexpected(request)
        val dependency = handler.unavailable(request)

        assertEquals(1.0, registry.counter("credit.evaluation.errors", "type", "INTERNAL").count())
        assertEquals(1.0, registry.counter("credit.evaluation.errors", "type", "DEPENDENCY").count())
        assertEquals("runtime-correlation", internal.body!!.correlationId)
        assertEquals("runtime-correlation", dependency.body!!.correlationId)
    }

    @Test
    // @spec:AC-063
    fun `AC-063 readiness fails independently for postgres and kafka`() {
        val health = DependencyReadinessIndicator(
            mapOf(
                "postgres" to RequiredDependencyProbe { true },
                "kafka" to RequiredDependencyProbe { false },
            )
        ).health()

        assertEquals("DOWN", health.status.code)
        assertEquals(listOf("kafka"), health.details["unavailableDependencies"])
    }

    @Test
    // @spec:AC-064
    fun `AC-064 operational configuration separates probes from sensitive endpoints`() {
        val security = java.io.File("src/main/kotlin/com/itau/credit/infrastructure/security/SecurityConfiguration.kt").readText()
        val observability = java.io.File("src/main/resources/application-observability.yml").readText()

        require(security.contains("/actuator/health/liveness\", \"/actuator/health/readiness\").permitAll()"))
        require(security.contains("/actuator/prometheus\", \"/actuator/**\").hasAuthority(\"SCOPE_credit:admin\")"))
        require(observability.contains("include: livenessState"))
        require(observability.contains("include: readinessState,dependencyReadiness"))
    }

    private fun response() = CreditEvaluationResponse(
        UUID.randomUUID(),
        "Cliente",
        "***.***.***-25",
        "REJECTED",
        BigDecimal.ZERO,
        "2026.08",
        listOf(RuleResponse("MINIMUM_SCORE", "Minimum score", "FAILED", "Score below threshold")),
        OffsetDateTime.parse("2026-08-16T12:00:00Z"),
        10,
        "runtime-correlation",
    )
}
