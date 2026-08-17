package io.github.brdoliveira.creditflow.platform.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority

/** Converte perfis do realm local nas mesmas autoridades usadas por clientes JWT. */
class OidcSessionAuthoritiesMapper : GrantedAuthoritiesMapper {
    /** Mapeia as autoridades OIDC para os escopos reconhecidos pela aplicação. */
    override fun mapAuthorities(authorities: Collection<GrantedAuthority>): Collection<GrantedAuthority> =
        authorities.flatMap { authority ->
            if (authority is OidcUserAuthority) authority.realmRoles().map(::SimpleGrantedAuthority) else listOf(authority)
        }.toSet()

    @Suppress("UNCHECKED_CAST")
    private fun OidcUserAuthority.realmRoles(): Set<String> =
        ((idToken.getClaim<Map<String, Any>>("realm_access") ?: emptyMap())["roles"] as? Collection<*>)
            ?.filterIsInstance<String>()
            ?.filter { it in CREDIT_SCOPES }
            ?.map { "SCOPE_$it" }
            ?.toSet()
            ?: emptySet()

    private companion object {
        val CREDIT_SCOPES = setOf("credit:write", "credit:read", "credit:report", "credit:admin")
    }
}
