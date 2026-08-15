package com.itau.credit.infrastructure.web

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class FrontendSmokeTest {
    @Test
    fun `@spec:AC-041 evaluation screen exposes the explainable decision area`() {
        val page = resource("static/index.html")
        val script = resource("static/ts/evaluation.ts")
        assertTrue(page.contains("id=\"decision\""))
        assertTrue(script.contains("renderDecision"))
    }

    @Test
    fun `@spec:AC-042 report screen reuses one filter query for listing and PDF`() {
        val script = resource("static/ts/report.ts")
        assertTrue(script.contains("filterQuery(filters())"))
        assertTrue(script.contains("/report.pdf?"))
    }

    @Test
    fun `@spec:AC-043 frontend error presentation is generic and correlation-aware`() {
        val script = resource("static/ts/api.ts")
        assertTrue(script.contains("Código de acompanhamento"))
        assertTrue(script.contains("Não foi possível concluir a operação agora"))
    }

    private fun resource(path: String): String = checkNotNull(javaClass.classLoader.getResource(path)) { "Missing $path" }.readText()
}
