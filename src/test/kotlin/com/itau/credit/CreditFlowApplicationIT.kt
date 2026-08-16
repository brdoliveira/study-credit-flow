package com.itau.credit

import com.itau.credit.application.report.CreditEvaluationReportFilter
import com.itau.credit.infrastructure.web.CreditEvaluationApiService
import com.itau.credit.infrastructure.web.CreditEvaluationReportService
import com.itau.credit.infrastructure.web.CreditEvaluationRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Testcontainers
class CreditFlowApplicationIT @Autowired constructor(
    private val apiService: CreditEvaluationApiService,
    private val reportService: CreditEvaluationReportService,
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    // @spec:AC-001
    fun `AC-001 complete Spring context creates queries and reports one evaluation`() {
        val correlationId = "operation-context-1"
        val created = apiService.evaluate(request(), UUID.randomUUID().toString(), correlationId)
        val restored = apiService.findById(created.evaluationId, correlationId)
        val pdf = reportService.generate(CreditEvaluationReportFilter(), Instant.parse("2026-08-16T12:00:00Z"), correlationId)

        assertEquals("APPROVED", created.decision)
        assertEquals(correlationId, created.correlationId)
        assertEquals(created.evaluationId, restored?.evaluationId)
        assertTrue(pdf.take(4).toByteArray().contentEquals("%PDF".toByteArray()))
    }

    private fun request() = CreditEvaluationRequest(
        "Ana",
        "52998224725",
        720,
        BigDecimal("1800.00"),
        BigDecimal("5000.00"),
        BigDecimal("4000.00"),
        0,
        listOf(BigDecimal("1500.00"), BigDecimal("1700.00"), BigDecimal("1800.00")),
    )

    private companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
