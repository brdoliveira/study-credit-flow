package io.github.brdoliveira.creditflow.infrastructure.web

/** Dados públicos da sessão autenticada. */
data class SessionResponse(
    val authenticated: Boolean,
    val authorities: List<String>,
    val csrfToken: String,
)
