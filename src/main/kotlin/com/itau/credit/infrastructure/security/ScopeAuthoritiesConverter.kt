package com.itau.credit.infrastructure.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Converts OAuth/OIDC scopes, including Keycloak realm roles used locally, to
 * Spring Security authorities. Keeping the application authorization model in
 * scopes makes it portable to another OIDC provider in production.
 */
class ScopeAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(jwt: Jwt): Collection<GrantedAuthority> =
        (jwt.scopeValues() + jwt.realmRoles())
            .filter { it in CREDIT_SCOPES }
            .map { SimpleGrantedAuthority("SCOPE_$it") }
            .toSet()

    private fun Jwt.scopeValues(): Set<String> = sequenceOf("scope", "scp")
        .flatMap { claim ->
            when (val value = getClaim<Any>(claim)) {
                is String -> value.split(Regex("\\s+")).asSequence()
                is Collection<*> -> value.asSequence().filterIsInstance<String>()
                else -> emptySequence()
            }
        }
        .filter(String::isNotBlank)
        .toSet()

    @Suppress("UNCHECKED_CAST")
    private fun Jwt.realmRoles(): Set<String> =
        ((getClaim<Map<String, Any>>("realm_access") ?: emptyMap())["roles"] as? Collection<*>)
            ?.filterIsInstance<String>()
            ?.toSet()
            ?: emptySet()

    private companion object {
        val CREDIT_SCOPES = setOf("credit:write", "credit:read", "credit:report", "credit:admin")
    }
}
