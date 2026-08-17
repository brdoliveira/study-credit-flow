package io.github.brdoliveira.creditflow.evaluation.infrastructure.idempotency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CanonicalRequestHasherTest {
    private val hasher = CanonicalRequestHasher()

    @Test
    fun `semantically equivalent JSON produces the same hash`() {
        assertThat(hasher.hash("{\"score\":720,\"customer\":{\"name\":\"Ana\"}}"))
            .isEqualTo(hasher.hash("{ \"customer\" : { \"name\" : \"Ana\" }, \"score\" : 720 }"))
    }

    @Test
    fun `opaque canonical application source can be hashed`() {
        val source = "Ana|12345678909|720|100.00|1000.00|900.00|0|10.00,20.00,30.00"

        assertThat(hasher.hash(source)).hasSize(64)
        assertThat(hasher.hash(source)).isEqualTo(hasher.hash(source))
    }
}
