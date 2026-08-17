package io.github.brdoliveira.creditflow.infrastructure.web

import org.springframework.security.core.Authentication
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/session")
/** Expõe os dados da sessão autenticada para o frontend. */
class SessionController {
    /** Retorna autenticação, perfis e token de proteção contra requisições forjadas. */
    @GetMapping
    fun current(authentication: Authentication, csrfToken: CsrfToken): SessionResponse = SessionResponse(
        authenticated = authentication.isAuthenticated,
        authorities = authentication.authorities.mapNotNull { it.authority }.sorted(),
        csrfToken = csrfToken.token,
    )
}
