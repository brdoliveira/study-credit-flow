package com.itau.credit.infrastructure.idempotency

import com.itau.credit.application.port.IdempotencyKeyConflictException
import com.itau.credit.application.port.InvalidIdempotencyKeyException
import com.itau.credit.application.port.MissingIdempotencyKeyException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(SpringExtension::class)
class IdempotencyIT @Autowired constructor(
    entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
) {
    private val repository = PostgresIdempotencyRepository(entityManager, TransactionTemplate(transactionManager))

    @Test
    fun `@spec:AC-018 missing or malformed idempotency key is rejected before an operation is created`() {
        assertThatThrownBy { repository.execute(null, "{}") { "{\"evaluationId\":\"new\"}" } }
            .isInstanceOf(MissingIdempotencyKeyException::class.java)
        assertThatThrownBy { repository.execute("not-a-uuid", "{}") { "{\"evaluationId\":\"new\"}" } }
            .isInstanceOf(InvalidIdempotencyKeyException::class.java)
    }

    @Test
    fun `@spec:AC-019 identical canonical request replays the original evaluation result`() {
        val calls = AtomicInteger()
        val key = UUID.randomUUID().toString()
        val original = repository.execute(key, "{\"score\":720,\"customer\":{\"name\":\"Ana\"}}") {
            calls.incrementAndGet(); "{\"evaluationId\":\"evaluation-1\"}"
        }
        val replay = repository.execute(key, "{ \"customer\" : { \"name\" : \"Ana\" }, \"score\" : 720 }") {
            calls.incrementAndGet(); "{\"evaluationId\":\"evaluation-2\"}"
        }

        assertThat(replay).isEqualTo(original)
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `@spec:AC-020 divergent reuse is rejected and preserves the original result`() {
        val key = UUID.randomUUID().toString()
        val original = repository.execute(key, "{\"score\":720}") { "{\"evaluationId\":\"evaluation-1\"}" }

        assertThatThrownBy { repository.execute(key, "{\"score\":650}") { "{\"evaluationId\":\"evaluation-2\"}" } }
            .isInstanceOf(IdempotencyKeyConflictException::class.java)
        assertThat(repository.execute(key, "{\"score\":720}") { error("must replay") }).isEqualTo(original)
    }

    @Test
    fun `@spec:AC-021 concurrent identical requests persist one evaluation and converge on its result`() {
        val calls = AtomicInteger()
        val key = UUID.randomUUID().toString()
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = List(2) {
                executor.submit(Callable {
                    barrier.await(5, TimeUnit.SECONDS)
                    repository.execute(key, "{\"score\":720}") {
                        calls.incrementAndGet()
                        Thread.sleep(150)
                        "{\"evaluationId\":\"evaluation-1\"}"
                    }
                })
            }.map { it.get(10, TimeUnit.SECONDS) }

            assertThat(results).containsOnly("{\"evaluationId\":\"evaluation-1\"}")
            assertThat(calls.get()).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

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
private class IdempotencyTestApplication
