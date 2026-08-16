package com.itau.credit.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.http.HttpStatus
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AnyRequestMatcher

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration(
    @Value("\${app.security.require-https:false}") private val requireHttps: Boolean
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val jwtAuthenticationConverter = JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(ScopeAuthoritiesConverter())
        }

        val csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        csrfRepository.setCookieCustomizer { cookie -> cookie.sameSite("Lax").secure(requireHttps) }

        http
            .csrf {
                it.csrfTokenRepository(csrfRepository)
                    .requireCsrfProtectionMatcher { request ->
                        request.method !in SAFE_METHODS && request.getSession(false) != null
                    }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                    .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                    .requestMatchers("/actuator/prometheus", "/actuator/**").hasAuthority("SCOPE_credit:admin")
                    .requestMatchers("/api/session").authenticated()
                    .requestMatchers("/api/v1/credit-evaluations/report/**").hasAuthority("SCOPE_credit:report")
                    .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_credit:admin")
                    .requestMatchers(HttpMethod.POST, "/api/v1/credit-evaluations/**").hasAuthority("SCOPE_credit:write")
                    .requestMatchers(HttpMethod.GET, "/api/v1/credit-evaluations/**").hasAuthority("SCOPE_credit:read")
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                it.defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), PathPatternRequestMatcher.pathPattern("/api/**"))
                    .defaultAuthenticationEntryPointFor(LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak"), AnyRequestMatcher.INSTANCE)
            }
            .oauth2Login {
                it.authorizedClientRepository(HttpSessionOAuth2AuthorizedClientRepository())
                    .userInfoEndpoint { endpoint -> endpoint.userAuthoritiesMapper(OidcSessionAuthoritiesMapper()) }
                    .successHandler(SimpleUrlAuthenticationSuccessHandler())
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) } }
            .logout {
                it.logoutUrl("/api/session/logout")
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                    .logoutSuccessHandler(SimpleUrlLogoutSuccessHandler().apply {
                        setDefaultTargetUrl("/")
                        setAlwaysUseDefaultTargetUrl(true)
                    })
            }

        if (requireHttps) {
            http.redirectToHttps { }
        }

        return http.build()
    }

    private companion object {
        val SAFE_METHODS = setOf("GET", "HEAD", "TRACE", "OPTIONS")
    }
}
