package com.itau.credit.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

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

        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/v1/credit-evaluations/report/**").hasAuthority("SCOPE_credit:report")
                    .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_credit:admin")
                    .requestMatchers(HttpMethod.POST, "/api/v1/credit-evaluations/**").hasAuthority("SCOPE_credit:write")
                    .requestMatchers(HttpMethod.GET, "/api/v1/credit-evaluations/**").hasAuthority("SCOPE_credit:read")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) } }

        if (requireHttps) {
            http.requiresChannel { it.anyRequest().requiresSecure() }
        }

        return http.build()
    }
}
