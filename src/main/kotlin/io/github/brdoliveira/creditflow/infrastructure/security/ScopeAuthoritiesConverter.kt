package io.github.brdoliveira.creditflow.infrastructure.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/** Converte escopos OAuth/OIDC e perfis locais em autoridades do Spring Security. */
class ScopeAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    /** Extrai somente os escopos autorizados pela aplicação. */
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
