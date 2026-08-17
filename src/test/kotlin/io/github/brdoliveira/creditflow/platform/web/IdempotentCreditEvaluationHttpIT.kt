package io.github.brdoliveira.creditflow.platform.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdempotentCreditEvaluationHttpIT @Autowired constructor(
    private val mvc: MockMvc,
    private val jdbcTemplate: JdbcTemplate,
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    // @spec:AC-053
    fun `AC-053 first HTTP execution creates an evaluation and identifies a non-replay`() {
        val result = request(UUID.randomUUID().toString(), validRequest())

        result.andExpect(status().isCreated)
            .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/credit-evaluations/[0-9a-f-]{36}")))
            .andExpect(header().string("Idempotency-Replayed", "false"))
    }

    @Test
    // @spec:AC-054
    fun `AC-054 replayed HTTP execution returns the original body without another creation`() {
        val key = UUID.randomUUID().toString()
        val evaluationsBefore = count("credit_evaluation")
        val outboxBefore = count("credit_outbox")
        val created = request(key, validRequest()).andReturn().response
        val replay = request(key, validRequest()).andReturn().response

        assertThat(created.status).isEqualTo(201)
        assertThat(replay.status).isEqualTo(200)
        assertThat(replay.getHeader("Idempotency-Replayed")).isEqualTo("true")
        assertThat(replay.contentAsString).isEqualTo(created.contentAsString)
        assertThat(count("credit_evaluation")).isEqualTo(evaluationsBefore + 1)
        assertThat(count("credit_outbox")).isEqualTo(outboxBefore + 1)
    }

    @Test
    // @spec:AC-055
    fun `AC-055 concurrent real HTTP calls converge and a divergent payload conflicts in PostgreSQL`() {
        val key = UUID.randomUUID().toString()
        val evaluationsBefore = count("credit_evaluation")
        val outboxBefore = count("credit_outbox")
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val responses = List(2) {
                executor.submit(Callable {
                    barrier.await(5, TimeUnit.SECONDS)
                    request(key, validRequest()).andReturn().response
                })
            }.map { it.get(15, TimeUnit.SECONDS) }

            assertThat(responses.map { it.status }).containsExactlyInAnyOrder(201, 200)
            assertThat(responses.map { it.contentAsString }.distinct()).hasSize(1)
            request(key, validRequest(creditScore = 650)).andExpect(status().isConflict)
            assertThat(count("credit_evaluation")).isEqualTo(evaluationsBefore + 1)
            assertThat(count("credit_outbox")).isEqualTo(outboxBefore + 1)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun request(key: String, body: String) = mvc.perform(
        post("/api/v1/credit-evaluations")
            .with(jwt().jwt { it.claim("scope", "credit:write") })
            .header("Idempotency-Key", key)
            .contentType("application/json")
            .content(body),
    )

    private fun validRequest(creditScore: Int = 720) = """{"name":"Ana","cpf":"52998224725","creditScore":$creditScore,"currentInvoiceAmount":1800.00,"totalLimit":5000.00,"availableLimit":4000.00,"latePayments":0,"monthlySpending":[1500.00,1700.00,1800.00]}"""

    private fun count(table: String): Long = jdbcTemplate.queryForObject("select count(*) from $table", Long::class.java)!!

    private companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            postgres.start()
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
