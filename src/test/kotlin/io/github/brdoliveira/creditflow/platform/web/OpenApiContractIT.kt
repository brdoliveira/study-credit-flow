package io.github.brdoliveira.creditflow.platform.web

import io.github.brdoliveira.creditflow.support.CreditEvaluationControllerFixture
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class OpenApiContractIT {
    private val evaluationId = UUID.fromString("d2719d1c-f0db-4c3d-9de2-4d7cfd6d4d7e")

    @Test
    @DisplayName("@spec:AC-074 OpenAPI descreve operações, contratos, escopos e exemplos mascarados")
    fun openApiDescribesRelevantOperationsAndContracts() {
        val contract = contract()
        listOf("/api/v1/credit-evaluations:", "/api/v1/credit-evaluations/{evaluationId}:", "/api/v1/credit-evaluations/report.pdf:", "Idempotency-Key", "Idempotency-Replayed", "X-Correlation-ID", "credit:read", "credit:write", "credit:report", "credit:admin", "ReportTooLarge", "'422':", "maskedCpf:", "fieldErrors:", "page:", "size:", "sort:").forEach { expected ->
            assertTrue(contract.contains(expected), "Contract must contain $expected")
        }
        assertTrue(contract.contains("'***.982.247-**'"), "Response CPF example must be masked")
        assertTrue(!contract.contains("cpf: '12345678901'"), "Contract must not use an unmasked placeholder CPF")
    }

    @Test
    @DisplayName("@spec:AC-074 Swagger UI usa o contrato detalhado em português")
    fun swaggerUiUsesTheDocumentedContract() {
        val contract = contract()
        listOf(
            "summary: Criar uma avaliação de crédito",
            "summary: Consultar avaliações de crédito",
            "summary: Consultar uma avaliação pelo identificador",
            "summary: Baixar relatório de avaliações em PDF",
            "description: Filtra pelo resultado da avaliação.",
            "description: Data inicial inclusiva do período",
            "description: Data final inclusiva do período",
            "description: Número da página",
            "description: Quantidade de avaliações por página",
        ).forEach { expected ->
            assertTrue(contract.contains(expected), "Contract must document $expected")
        }

        val configuration = ClassPathResource("application.yml").inputStream.bufferedReader().use { it.readText() }
        assertTrue(configuration.contains("url: /openapi/credit-evaluations.yaml"))
        assertTrue(configuration.contains("disable-swagger-default-url: true"))

        val openApi = Yaml.mapper().readValue(contract, OpenAPI::class.java)
        val collectionPath = requireNotNull(openApi.paths["/api/v1/credit-evaluations"])
        val decisionParameter = requireNotNull(openApi.components.parameters["Decision"])
        val pageParameter = requireNotNull(openApi.components.parameters["Page"])
        assertEquals("Consultar avaliações de crédito", collectionPath.get.summary)
        assertEquals(
            "Filtra pelo resultado da avaliação. Use `APPROVED` para aprovadas ou `REJECTED` para reprovadas.",
            decisionParameter.description,
        )
        assertEquals("Número da página, iniciado em zero.", pageParameter.description)
    }

    @Test
    @DisplayName("@spec:AC-075 Contrato OpenAPI acompanha métodos, status e cabeçalhos HTTP")
    fun openApiStaysAlignedWithHttpBehaviour() {
        val contract = contract()
        assertTrue(contract.contains("'201':"))
        assertTrue(contract.contains("Location:"))
        assertTrue(contract.contains("'404':"))
        mvc().perform(post("/api/v1/credit-evaluations").header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isCreated).andExpect(header().string("Location", "/api/v1/credit-evaluations/$evaluationId"))
        mvc().perform(get("/api/v1/credit-evaluations/$evaluationId")).andExpect(status().isOk)
        mvc().perform(get("/api/v1/credit-evaluations?page=1&size=10&sort=approvedAmount&direction=ASC")).andExpect(status().isOk)
    }

    private fun contract(): String = ClassPathResource("openapi/credit-evaluations.yaml").inputStream.bufferedReader().use { it.readText() }
    private fun mvc(): MockMvc = MockMvcBuilders.standaloneSetup(CreditEvaluationControllerFixture.controller()).build()
    private fun validRequest() = """{"name":"Ana Silva","cpf":"52998224725","creditScore":720,"currentInvoiceAmount":1800.00,"totalLimit":5000.00,"availableLimit":4000.00,"latePayments":0,"monthlySpending":[1500.00,1700.00,1800.00]}"""
}
