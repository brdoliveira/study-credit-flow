package io.github.brdoliveira.creditflow.platform.config

import org.springframework.boot.web.server.MimeMappings
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Configura tipos de conteúdo dos módulos estáticos executados pelo navegador. */
@Configuration(proxyBeanMethods = false)
class StaticAssetConfiguration {
    /** Impede que o Tomcat trate módulos TypeScript compatíveis com JavaScript como vídeo MPEG. */
    @Bean
    fun staticAssetMimeMappings(): WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            factory.addMimeMappings(MimeMappings(mapOf("ts" to "text/javascript")))
        }
}
