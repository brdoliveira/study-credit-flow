package io.github.brdoliveira.creditflow.platform.security

import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationResult
import io.github.brdoliveira.creditflow.evaluation.application.CreateCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPage
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.application.EvaluateRevolvingCreditUseCase
import io.github.brdoliveira.creditflow.evaluation.application.FindCreditEvaluationUseCase
import io.github.brdoliveira.creditflow.evaluation.application.ListCreditEvaluationsUseCase
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationMetrics
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyExecution
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyRepository
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus
import io.github.brdoliveira.creditflow.evaluation.domain.calculation.CreditLimitCalculator
import io.github.brdoliveira.creditflow.evaluation.domain.rule.CreditRule
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RuleEngine
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller.CreditEvaluationController
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.mapper.CreditEvaluationWebMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@WebMvcTest(controllers = [CreditEvaluationController::class])
@Import(SecurityConfiguration::class, SecurityProbeController::class, SecurityTestServices::class)
class ApiSecurityTest @Autowired constructor(
    private val mvc: MockMvc
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun rejectInvalidToken() {
        doThrow(BadJwtException("invalid token")).`when`(jwtDecoder).decode("invalid-token")
    }

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

}

@WebMvcTest(controllers = [SecurityProbeController::class], properties = ["app.security.require-https=true"])
@Import(SecurityConfiguration::class, SecurityProbeController::class)
class ProductionTransportSecurityTest @Autowired constructor(
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

@TestConfiguration(proxyBeanMethods = false)
class SecurityTestServices {
    @Bean
    fun creditEvaluationRepository(): CreditEvaluationRepository = object : CreditEvaluationRepository {
        override fun save(evaluation: CreditEvaluation) = evaluation
        override fun findById(evaluationId: UUID) = evaluation(evaluationId)
        override fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest) =
            CreditEvaluationPage(emptyList(), 0, page.page, page.size, page.sort)
    }

    @Bean
    fun createCreditEvaluationUseCase(repository: CreditEvaluationRepository): CreateCreditEvaluationUseCase {
        val rule = object : CreditRule {
            override val code = "MINIMUM_SCORE"
            override val name = "Minimum score"
            override val severity = RuleSeverity.BLOCKING
            override fun evaluate(context: CreditEvaluationContext) =
                RuleResult(code, name, severity, RuleStatus.PASSED, "passed")
        }
        val calculator = object : CreditLimitCalculator {
            override fun calculate(availableLimit: BigDecimal, creditScore: Int, eligible: Boolean) = BigDecimal("2800.00")
        }
        val evaluator = EvaluateRevolvingCreditUseCase(
            RuleEngine(listOf(rule)), calculator, repository,
            Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC),
        )
        val idempotency = object : IdempotencyRepository {
            override fun execute(key: String?, requestBody: String, operation: () -> CreateCreditEvaluationResult) =
                IdempotencyExecution(operation(), replayed = false)
        }
        return CreateCreditEvaluationUseCase(evaluator, idempotency, CreditEvaluationMetrics { _, _ -> })
    }

    @Bean
    fun findCreditEvaluationUseCase(repository: CreditEvaluationRepository) = FindCreditEvaluationUseCase(repository)

    @Bean
    fun listCreditEvaluationsUseCase(repository: CreditEvaluationRepository) = ListCreditEvaluationsUseCase(repository)

    @Bean
    fun creditEvaluationWebMapper() = CreditEvaluationWebMapper()

    private fun evaluation(evaluationId: UUID) = CreditEvaluation(
        evaluationId, "***.***.***-25", CreditDecisionStatus.APPROVED,
        listOf(RuleResult("MINIMUM_SCORE", "Minimum score", RuleSeverity.BLOCKING, RuleStatus.PASSED, "passed")),
        BigDecimal("2800.00"), "v1", Instant.parse("2026-08-15T10:00:00Z"), 21, "trace-1",
    )
}
