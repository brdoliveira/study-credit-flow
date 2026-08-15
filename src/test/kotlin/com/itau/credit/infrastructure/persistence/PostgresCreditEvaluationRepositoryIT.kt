package com.itau.credit.infrastructure.persistence

import com.itau.credit.application.port.CreditEvaluationFilter
import com.itau.credit.application.port.CreditEvaluationPageRequest
import com.itau.credit.application.port.CreditEvaluationSnapshot
import com.itau.credit.application.port.CreditEvaluationSort
import com.itau.credit.infrastructure.privacy.CpfProtector
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(SpringExtension::class)
class PostgresCreditEvaluationRepositoryIT @Autowired constructor(
    private val entityManager: EntityManager,
) {
    private val repository by lazy { PostgresCreditEvaluationRepository(entityManager) }
    private val cpfProtector = CpfProtector()

    @Test
    fun `@spec:AC-015 persisted result preserves every traceability field`() {
        val snapshot = snapshot(cpfProtector.mask("12345678909"))
        repository.save(snapshot)
        entityManager.flush()
        entityManager.clear()

        assertThat(repository.findById(snapshot.evaluationId)).isEqualTo(snapshot)
    }

    @Test
    fun `@spec:AC-016 decision snapshot remains queryable after persistence`() {
        val snapshot = snapshot(cpfProtector.mask("12345678909"), ruleResults = "[{\"code\":\"MINIMUM_SCORE\",\"status\":\"PASSED\"}]")
        repository.save(snapshot)
        entityManager.flush()
        entityManager.clear()

        val restored = repository.findById(snapshot.evaluationId)!!
        assertThat(restored.decision).isEqualTo("APPROVED")
        assertThat(restored.approvedAmount).isEqualByComparingTo("1200.50")
        assertThat(restored.ruleVersion).isEqualTo("2026.08")
        assertThat(restored.ruleResults).contains("MINIMUM_SCORE")
    }

    @Test
    fun `@spec:AC-017 full CPF is neither stored nor returned`() {
        val fullCpf = "12345678909"
        repository.save(snapshot(cpfProtector.mask(fullCpf)))
        entityManager.flush()

        val persistedValues = entityManager.createNativeQuery("select cpf_masked from credit_evaluation").resultList
        assertThat(persistedValues).containsExactly("***.***.***-09")
        assertThat(persistedValues.joinToString()).doesNotContain(fullCpf)
    }

    @Test
    fun `@spec:AC-022 list is filtered paginated and ordered`() {
        val first = snapshot(cpfProtector.mask("12345678909"), evaluatedAt = Instant.parse("2026-08-01T10:00:00Z"))
        val approved = snapshot(cpfProtector.mask("98765432100"), decision = "APPROVED", evaluatedAt = Instant.parse("2026-08-02T10:00:00Z"))
        val third = snapshot(cpfProtector.mask("11122233344"), decision = "APPROVED", evaluatedAt = Instant.parse("2026-08-03T10:00:00Z"))
        listOf(first, approved, third).forEach(repository::save)
        entityManager.flush()

        val result = repository.findPage(
            CreditEvaluationFilter(decision = "APPROVED", from = Instant.parse("2026-08-02T00:00:00Z")),
            CreditEvaluationPageRequest(page = 0, size = 1, sort = CreditEvaluationSort.EVALUATED_AT_ASC),
        )
        assertThat(result.total).isEqualTo(2)
        assertThat(result.items).containsExactly(approved)
        assertThat(result.page).isZero()
        assertThat(result.size).isEqualTo(1)
        assertThat(result.sort).isEqualTo(CreditEvaluationSort.EVALUATED_AT_ASC)
    }

    @Test
    fun `@spec:AC-023 evaluation is found by its identifier`() {
        val snapshot = snapshot(cpfProtector.mask("12345678909"))
        repository.save(snapshot)
        entityManager.flush()

        assertThat(repository.findById(snapshot.evaluationId)?.evaluationId).isEqualTo(snapshot.evaluationId)
    }

    @Test
    fun `@spec:AC-024 missing evaluation has no repository result for standardized not-found mapping`() {
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    private fun snapshot(
        maskedCpf: String,
        decision: String = "APPROVED",
        ruleResults: String = "[]",
        evaluatedAt: Instant = Instant.parse("2026-08-01T10:00:00Z"),
    ) = CreditEvaluationSnapshot(
        UUID.randomUUID(), maskedCpf, decision, BigDecimal("1200.50"), "2026.08", ruleResults, evaluatedAt, 42, UUID.randomUUID(),
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

@SpringBootConfiguration
@EnableAutoConfiguration
private class PersistenceTestApplication
