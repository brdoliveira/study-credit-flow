package com.itau.credit.infrastructure.security

import com.itau.credit.infrastructure.web.SessionController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.mock.web.MockHttpSession
import java.nio.file.Files
import java.nio.file.Path

@WebMvcTest(controllers = [SessionController::class, BrowserSecurityProbeController::class])
@Import(SecurityConfiguration::class)
class OidcBrowserSecurityIT @Autowired constructor(private val mvc: MockMvc) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    // @spec:AC-048
    fun `AC-048 redirects an interactive browser request to the corporate OIDC login`() {
        mvc.perform(get("/index.html"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, "/oauth2/authorization/keycloak"))
    }

    @Test
    // @spec:AC-049
    fun `AC-049 keeps OAuth tokens server-side and requires CSRF for session mutations`() {
        val session = MockHttpSession()
        mvc.perform(get("/api/session").session(session).with(browser("credit:write")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.csrfToken").isNotEmpty)
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.clientSecret").doesNotExist())

        mvc.perform(post("/api/v1/credit-evaluations/browser").session(session).with(browser("credit:write")).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)

        mvc.perform(post("/api/v1/credit-evaluations/browser").session(session).with(browser("credit:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
    }

    @Test
    // @spec:AC-050
    fun `AC-050 returns 401 for anonymous APIs and 403 when browser permissions are insufficient`() {
        mvc.perform(get("/api/v1/credit-evaluations/browser"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/credit-evaluations/browser").with(browser("credit:write")))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/credit-evaluations/browser").with(browser("credit:read")))
            .andExpect(status().isOk)
    }

    @Test
    // @spec:AC-051
    fun `AC-051 logout invalidates the local session and uses the approved post logout URL`() {
        val session = MockHttpSession()
        mvc.perform(post("/api/session/logout").session(session).with(browser("credit:read")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, "/"))
        mvc.perform(get("/api/v1/credit-evaluations/browser").session(session))
            .andExpect(status().isUnauthorized)
    }

    @Test
    // @spec:AC-052
    fun `AC-052 uses the same public issuer with an internal Keycloak backchannel`() {
        val compose = Files.readString(Path.of("compose.yaml"))
        val security = Files.readString(Path.of("src/main/resources/application-security.yml"))
        val realm = Files.readString(Path.of("docker/keycloak/realm-export.json"))
        require(compose.contains("KC_HOSTNAME: http://localhost:"))
        require(compose.contains("KC_HOSTNAME_BACKCHANNEL_DYNAMIC: \"true\""))
        require(compose.contains("KEYCLOAK_ISSUER_URI: http://localhost:"))
        require(compose.contains("JWT_JWK_SET_URI: http://keycloak:8080/realms/credit-rotativo"))
        require(compose.contains("PROVIDER_KEYCLOAK_TOKEN_URI: http://keycloak:8080/realms/credit-rotativo"))
        require(security.contains("issuer-uri: \${KEYCLOAK_ISSUER_URI:"))
        require(realm.contains("oidc-usermodel-realm-role-mapper"))
        require(realm.contains("\"id.token.claim\": \"true\""))
    }

    private fun browser(scope: String) = oauth2Login().authorities(SimpleGrantedAuthority("SCOPE_$scope"))
}

@RestController
@RequestMapping("/api/v1/credit-evaluations")
class BrowserSecurityProbeController {
    @GetMapping("/browser")
    fun read() = mapOf("status" to "ok")

    @PostMapping("/browser")
    fun write() = mapOf("status" to "ok")
}
