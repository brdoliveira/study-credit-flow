package io.github.brdoliveira.creditflow.platform.web

import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.CreditEvaluationSearchCriteria
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error.GlobalExceptionHandler
import io.github.brdoliveira.creditflow.support.CreditEvaluationControllerFixture
import org.assertj.core.api.Assertions.assertThat
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

class CreditEvaluationControllerTest {
    private val evaluationId = CreditEvaluationControllerFixture.evaluationId

    @Test
    // @spec:AC-001
    fun `AC-001 creates a valid evaluation and returns its location`() {
        mvc().perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/v1/credit-evaluations/$evaluationId"))
            .andExpect(jsonPath("$.evaluationId").value(evaluationId.toString()))
    }

    @Test
    // @spec:AC-002
    fun `AC-002 explains every invalid input field`() {
        mvc().perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content("""{"name":"","cpf":"123","creditScore":1001,"currentInvoiceAmount":-1,"totalLimit":0,"availableLimit":-1,"latePayments":-1,"monthlySpending":[1,-1]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'cpf')]").exists())
            .andExpect(jsonPath("$.fieldErrors[?(@.field == 'creditScore')]").exists())
    }

    @Test
    // @spec:AC-003
    fun `AC-003 returns rejected evaluation as a successful response`() {
        val rejected = CreditEvaluationControllerFixture.evaluation(CreditDecisionStatus.REJECTED, BigDecimal.ZERO)
        mvc(CreditEvaluationControllerFixture.controller(rejected)).perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.decision").value("REJECTED"))
            .andExpect(jsonPath("$.approvedAmount").value(0))
    }

    @Test
    // @spec:AC-022
    fun `AC-022 lists evaluations with pagination filters and ordering`() {
        mvc().perform(get("/api/v1/credit-evaluations?decision=APPROVED&from=2026-08-01&to=2026-08-15&page=1&size=10&sort=approvedAmount&direction=ASC"))
            .andExpect(status().isOk).andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(10)).andExpect(jsonPath("$.sort").value("approvedAmount,ASC"))
    }

    @Test
    // @spec:AC-093
    fun `AC-093 converts the accepted HTTP decision filter to a domain status`() {
        val criteria = CreditEvaluationSearchCriteria("APPROVED", null, null, 0, 20, "processedAt", "DESC")

        criteria.validate(setOf("decision"))

        assertThat(criteria.toDecision()).isEqualTo(CreditDecisionStatus.APPROVED)
    }

    @Test
    // @spec:AC-023
    fun `AC-023 retrieves a persisted evaluation by identifier`() {
        mvc().perform(get("/api/v1/credit-evaluations/$evaluationId")).andExpect(status().isOk)
            .andExpect(jsonPath("$.rules[0].code").value("MINIMUM_SCORE"))
    }

    @Test
    // @spec:AC-024
    fun `AC-024 returns a standardized error for a missing evaluation`() {
        mvc(CreditEvaluationControllerFixture.controller(found = null)).perform(get("/api/v1/credit-evaluations/$evaluationId"))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty())
    }

    @Test
    // @spec:AC-028
    fun `AC-028 rejects invalid and unknown list filters`() {
        mvc().perform(get("/api/v1/credit-evaluations?from=2026-08-15&to=2026-08-01&unexpected=true"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_FILTER"))
    }

    @Test
    // @spec:AC-040
    fun `AC-040 returns a correlated internal error without stack trace`() {
        mvc(CreditEvaluationControllerFixture.controller(failure = IllegalStateException("database password leaked")))
            .perform(get("/api/v1/credit-evaluations/$evaluationId").header("X-Correlation-ID", "trace-123"))
            .andExpect(status().isInternalServerError).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.correlationId").value("trace-123")).andExpect(jsonPath("$.message").value("An unexpected error occurred"))
    }

    private fun mvc(controller: Any = CreditEvaluationControllerFixture.controller()): MockMvc {
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler()).setValidator(validator).build()
    }

    private fun validRequest() = """{"name":"Ana","cpf":"52998224725","creditScore":720,"currentInvoiceAmount":1800.00,"totalLimit":5000.00,"availableLimit":4000.00,"latePayments":0,"monthlySpending":[1500.00,1700.00,1800.00]}"""
}
