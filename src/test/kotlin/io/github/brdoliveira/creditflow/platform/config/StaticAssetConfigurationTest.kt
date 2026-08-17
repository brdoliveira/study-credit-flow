package io.github.brdoliveira.creditflow.platform.config

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StaticAssetConfigurationTest {
    @Test
    fun `TypeScript browser modules are served as JavaScript`() {
        val factory = TomcatServletWebServerFactory()

        StaticAssetConfiguration().staticAssetMimeMappings().customize(factory)

        assertEquals("text/javascript", factory.settings.mimeMappings.get("ts"))
    }
}
