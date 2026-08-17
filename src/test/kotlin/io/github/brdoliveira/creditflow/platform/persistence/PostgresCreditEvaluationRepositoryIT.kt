package io.github.brdoliveira.creditflow.platform.persistence

import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationSort
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus
import io.github.brdoliveira.creditflow.evaluation.infrastructure.persistence.CreditEvaluationEntity
import io.github.brdoliveira.creditflow.evaluation.infrastructure.persistence.PostgresCreditEvaluationRepository
import io.github.brdoliveira.creditflow.platform.privacy.CpfProtector
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.ObjectMapper

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = [CreditEvaluationEntity::class])
@Testcontainers
@ExtendWith(SpringExtension::class)
class PostgresCreditEvaluationRepositoryIT @Autowired constructor(
    private val entityManager: EntityManager,
) {
    private val repository by lazy { PostgresCreditEvaluationRepository(entityManager, ObjectMapper()) }
    private val cpfProtector = CpfProtector()

    @Test
    // @spec:AC-015
    fun `AC-015 persisted result preserves every traceability field`() {
        val snapshot = snapshot(cpfProtector.mask("12345678909"))
        repository.save(snapshot)
        entityManager.flush()
        entityManager.clear()

        assertThat(repository.findById(snapshot.evaluationId)).isEqualTo(snapshot)
    }

    @Test
    // @spec:AC-016
    fun `AC-016 decision snapshot remains queryable after persistence`() {
        val snapshot = snapshot(
            cpfProtector.mask("12345678909"),
            ruleResults = listOf(RuleResult("MINIMUM_SCORE", "Minimum score", RuleSeverity.BLOCKING, RuleStatus.PASSED, "passed")),
        )
        repository.save(snapshot)
        entityManager.flush()
        entityManager.clear()

        val restored = repository.findById(snapshot.evaluationId)!!
        assertThat(restored.decision).isEqualTo(CreditDecisionStatus.APPROVED)
        assertThat(restored.approvedAmount).isEqualByComparingTo("1200.50")
        assertThat(restored.ruleSetVersion).isEqualTo("2026.08")
        assertThat(restored.ruleResults.map { it.code }).contains("MINIMUM_SCORE")
    }

    @Test
    // @spec:AC-017
    fun `AC-017 full CPF is neither stored nor returned`() {
        val fullCpf = "12345678909"
        repository.save(snapshot(cpfProtector.mask(fullCpf)))
        entityManager.flush()

        val persistedValues = entityManager.createNativeQuery("select cpf_masked from credit_evaluation").resultList
        assertThat(persistedValues).containsExactly("***.***.***-09")
        assertThat(persistedValues.joinToString()).doesNotContain(fullCpf)
    }

    @Test
    // @spec:AC-093
    fun `AC-093 list is filtered by the domain decision status`() {
        val first = snapshot(cpfProtector.mask("12345678909"), evaluatedAt = Instant.parse("2026-08-01T10:00:00Z"))
        val approved = snapshot(cpfProtector.mask("98765432100"), decision = CreditDecisionStatus.APPROVED, evaluatedAt = Instant.parse("2026-08-02T10:00:00Z"))
        val third = snapshot(cpfProtector.mask("11122233344"), decision = CreditDecisionStatus.APPROVED, evaluatedAt = Instant.parse("2026-08-03T10:00:00Z"))
        listOf(first, approved, third).forEach(repository::save)
        entityManager.flush()

        val result = repository.findPage(
            CreditEvaluationFilter(decision = CreditDecisionStatus.APPROVED, from = Instant.parse("2026-08-02T00:00:00Z")),
            CreditEvaluationPageRequest(page = 0, size = 1, sort = CreditEvaluationSort.EVALUATED_AT_ASC),
        )
        assertThat(result.total).isEqualTo(2)
        assertThat(result.items).containsExactly(approved)
        assertThat(result.page).isZero()
        assertThat(result.size).isEqualTo(1)
        assertThat(result.sort).isEqualTo(CreditEvaluationSort.EVALUATED_AT_ASC)
    }

    @Test
    // @spec:AC-023
    fun `AC-023 evaluation is found by its identifier`() {
        val snapshot = snapshot(cpfProtector.mask("12345678909"))
        repository.save(snapshot)
        entityManager.flush()

        assertThat(repository.findById(snapshot.evaluationId)?.evaluationId).isEqualTo(snapshot.evaluationId)
    }

    @Test
    // @spec:AC-024
    fun `AC-024 missing evaluation has no repository result for standardized not-found mapping`() {
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    private fun snapshot(
        maskedCpf: String,
        decision: CreditDecisionStatus = CreditDecisionStatus.APPROVED,
        ruleResults: List<RuleResult> = emptyList(),
        evaluatedAt: Instant = Instant.parse("2026-08-01T10:00:00Z"),
    ) = CreditEvaluation(
        UUID.randomUUID(), maskedCpf, decision, ruleResults, BigDecimal("1200.50"), "2026.08", evaluatedAt, 42, UUID.randomUUID().toString(),
    )

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

@SpringBootConfiguration
@EnableAutoConfiguration
@Suppress("unused")
private class PersistenceTestApplication
