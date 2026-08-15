package com.itau.credit.infrastructure.web

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class CreditEvaluationControllerTest {
    private val evaluationId = UUID.fromString("d2719d1c-f0db-4c3d-9de2-4d7cfd6d4d7e")

    @Test
    fun `@spec:AC-001 creates a valid evaluation and returns its location`() {
        mvc().perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/v1/credit-evaluations/$evaluationId"))
            .andExpect(jsonPath("$.evaluationId").value(evaluationId.toString()))
    }

    @Test
    fun `@spec:AC-002 explains every invalid input field`() {
        mvc().perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content("""{"name":"","cpf":"123","creditScore":1001,"currentInvoiceAmount":-1,"totalLimit":0,"availableLimit":-1,"latePayments":-1,"monthlySpending":[1,-1]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'cpf')]").exists())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'creditScore')]").exists())
    }

    @Test
    fun `@spec:AC-003 returns rejected evaluation as a successful response`() {
        mvc(FakeService(response = response(decision = "REJECTED", approvedAmount = BigDecimal.ZERO))).perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.decision").value("REJECTED"))
            .andExpect(jsonPath("$.approvedAmount").value(0))
    }

    @Test
    fun `@spec:AC-022 lists evaluations with pagination filters and ordering`() {
        mvc().perform(get("/api/v1/credit-evaluations?decision=APPROVED&from=2026-08-01&to=2026-08-15&page=1&size=10&sort=approvedAmount&direction=ASC"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.sort").value("approvedAmount,ASC"))
    }

    @Test
    fun `@spec:AC-023 retrieves a persisted evaluation by identifier`() {
        mvc().perform(get("/api/v1/credit-evaluations/$evaluationId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rules[0].code").value("MINIMUM_SCORE"))
    }

    @Test
    fun `@spec:AC-024 returns a standardized error for a missing evaluation`() {
        mvc(FakeService(found = null)).perform(get("/api/v1/credit-evaluations/$evaluationId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty())
    }

    @Test
    fun `@spec:AC-028 rejects invalid and unknown list filters`() {
        mvc().perform(get("/api/v1/credit-evaluations?from=2026-08-15&to=2026-08-01&unexpected=true"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_FILTER"))
    }

    @Test
    fun `@spec:AC-040 returns a correlated internal error without stack trace`() {
        mvc(FakeService(failure = IllegalStateException("database password leaked"))).perform(get("/api/v1/credit-evaluations/$evaluationId").header("X-Correlation-ID", "trace-123"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.correlationId").value("trace-123"))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
    }

    private fun mvc(service: CreditEvaluationApiService = FakeService()): MockMvc {
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
        return MockMvcBuilders.standaloneSetup(CreditEvaluationController(service)).setControllerAdvice(GlobalExceptionHandler()).setValidator(validator).build()
    }

    private fun validRequest() = """{"name":"Ana","cpf":"52998224725","creditScore":720,"currentInvoiceAmount":1800.00,"totalLimit":5000.00,"availableLimit":4000.00,"latePayments":0,"monthlySpending":[1500.00,1700.00,1800.00]}"""

    private fun response(decision: String = "APPROVED", approvedAmount: BigDecimal = BigDecimal("2800.00")) = CreditEvaluationResponse(evaluationId, "Ana", "***.982.247-**", decision, approvedAmount, "v1", listOf(RuleResponse("MINIMUM_SCORE", "Minimum score", "PASSED", "Score meets the threshold")), OffsetDateTime.parse("2026-08-15T10:00:00Z"), 21, "trace-1")

    private inner class FakeService(
        private val response: CreditEvaluationResponse = this@CreditEvaluationControllerTest.response(),
        private val found: CreditEvaluationResponse? = response,
        private val failure: RuntimeException? = null
    ) : CreditEvaluationApiService {
        override fun evaluate(request: CreditEvaluationRequest, idempotencyKey: String, correlationId: String): CreditEvaluationResponse = failure?.let { throw it } ?: response
        override fun findById(evaluationId: UUID, correlationId: String): CreditEvaluationResponse? = failure?.let { throw it } ?: found
        override fun list(criteria: CreditEvaluationSearchCriteria, correlationId: String): CreditEvaluationPageResponse = failure?.let { throw it } ?: CreditEvaluationPageResponse(listOf(response), 1, criteria.page, criteria.size, "${criteria.sort},${criteria.direction.uppercase()}")
    }
}
