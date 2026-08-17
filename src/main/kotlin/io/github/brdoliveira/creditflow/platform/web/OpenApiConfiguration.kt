package io.github.brdoliveira.creditflow.platform.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Configura os metadados e o fluxo OAuth2 exibidos pelo Swagger. */
@Configuration
class OpenApiConfiguration : WebMvcConfigurer {
    /** Publica o contrato versionado no endereco consumido pelo Swagger UI. */
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/openapi/**")
            .addResourceLocations("classpath:/openapi/")
    }

    /** Cria a descrição OpenAPI da API de avaliações. */
    @Bean
    fun creditEvaluationsOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info().title("Credit evaluations API")
                .version("v1")
                .description("Contrato versionado disponivel em /openapi/credit-evaluations.yaml.")
        )
        .addSecurityItem(SecurityRequirement().addList("oauth2"))
        .schemaRequirement("oauth2", SecurityScheme().type(SecurityScheme.Type.OAUTH2).flows(OAuthFlows().authorizationCode(
            OAuthFlow().authorizationUrl("/oauth2/authorization/keycloak").tokenUrl("/login/oauth2/code/keycloak").scopes(
                Scopes()
                    .addString("credit:read", "Read credit evaluations")
                    .addString("credit:write", "Create credit evaluations")
                    .addString("credit:report", "Generate reports")
                    .addString("credit:admin", "Access administration")
            )
        )))
}
