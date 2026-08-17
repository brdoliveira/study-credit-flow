package io.github.brdoliveira.creditflow.infrastructure.health

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

fun interface RequiredDependencyProbe {
    fun isAvailable(): Boolean
}

class DependencyReadinessIndicator(
    private val probes: Map<String, RequiredDependencyProbe>,
) : HealthIndicator {
    override fun health(): Health {
        val unavailable = probes.filterValues { !safelyAvailable(it) }.keys.sorted()
        return if (unavailable.isEmpty()) {
            Health.up().withDetail("requiredDependencies", probes.size).build()
        } else {
            Health.down().withDetail("unavailableDependencies", unavailable).build()
        }
    }

    private fun safelyAvailable(probe: RequiredDependencyProbe): Boolean =
        runCatching(probe::isAvailable).getOrDefault(false)
}
