package com.itau.credit.infrastructure.security

import com.itau.credit.infrastructure.web.CreditEvaluationApiService
import com.itau.credit.infrastructure.web.CreditEvaluationController
import com.itau.credit.infrastructure.web.CreditEvaluationRequest
import com.itau.credit.infrastructure.web.CreditEvaluationResponse
import com.itau.credit.infrastructure.web.RuleResponse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(controllers = [CreditEvaluationController::class])
@Import(SecurityConfiguration::class, SecurityProbeController::class)
class ApiSecurityTest(
    private val mvc: MockMvc
) {
    @MockitoBean
    private lateinit var service: CreditEvaluationApiService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    // @spec:AC-029
    fun `AC-029 rejects missing and invalid tokens without exposing internal data`() {
        mvc.perform(get("/api/v1/credit-evaluations/${UUID.randomUUID()}"))
            .andExpect(status().isUnauthorized)

        mvc.perform(get("/api/v1/credit-evaluations/${UUID.randomUUID()}").header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    // @spec:AC-030
    fun `AC-030 rejects authenticated users without the required permission`() {
        mvc.perform(get("/api/v1/credit-evaluations/${UUID.randomUUID()}").with(token("credit:write")))
            .andExpect(status().isForbidden)
    }

    @Test
    // @spec:AC-031
    fun `AC-031 separates evaluation read write report and administration by scope`() {
        given(service.evaluate(any(CreditEvaluationRequest::class.java), any(String::class.java), any(String::class.java))).willReturn(response())
        given(service.findById(any(UUID::class.java), any(String::class.java))).willReturn(response())

        mvc.perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(validRequest()).with(token("credit:write")))
            .andExpect(status().isCreated)
        mvc.perform(get("/api/v1/credit-evaluations/${UUID.randomUUID()}").with(token("credit:read")))
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/credit-evaluations/report/monthly").with(token("credit:report")))
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/admin/ping").with(token("credit:admin")))
            .andExpect(status().isOk)
        mvc.perform(get("/api/v1/credit-evaluations/${UUID.randomUUID()}").with(token("credit:report")))
            .andExpect(status().isForbidden)
    }

    private fun token(scope: String) = jwt().jwt { it.claim("scope", scope) }

    private fun validRequest() = """{"name":"Ana","cpf":"52998224725","creditScore":720,"currentInvoiceAmount":1800.00,"totalLimit":5000.00,"availableLimit":4000.00,"latePayments":0,"monthlySpending":[1500.00,1700.00,1800.00]}"""

    private fun response() = CreditEvaluationResponse(UUID.randomUUID(), "Ana", "***.982.247-**", "APPROVED", BigDecimal("2800.00"), "v1", listOf(RuleResponse("MINIMUM_SCORE", "Minimum score", "PASSED", "Score meets the threshold")), OffsetDateTime.parse("2026-08-15T10:00:00Z"), 21, "trace-1")
}

@WebMvcTest(controllers = [SecurityProbeController::class], properties = ["app.security.require-https=true"])
@Import(SecurityConfiguration::class, SecurityProbeController::class)
class ProductionTransportSecurityTest(
    private val mvc: MockMvc
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    // @spec:AC-032
    fun `AC-032 redirects production HTTP requests to HTTPS`() {
        mvc.perform(get("/api/v1/admin/ping").with(token("credit:admin")).secure(false))
            .andExpect(status().is3xxRedirection)
    }

    private fun token(scope: String) = jwt().jwt { it.claim("scope", scope) }
}

@RestController
class SecurityProbeController {
    @GetMapping("/api/v1/credit-evaluations/report/monthly")
    fun report(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/api/v1/admin/ping")
    fun admin(): Map<String, String> = mapOf("status" to "ok")
}
