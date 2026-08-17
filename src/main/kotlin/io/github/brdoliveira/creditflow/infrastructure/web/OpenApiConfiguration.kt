package io.github.brdoliveira.creditflow.infrastructure.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
    @Bean
    fun creditEvaluationsOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info().title("Credit evaluations API")
                .version("v1")
                .description("The versioned contract is available at /openapi/credit-evaluations.yaml.")
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
