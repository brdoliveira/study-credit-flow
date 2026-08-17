package io.github.brdoliveira.creditflow.platform.report

import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPage
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.application.ListCreditEvaluationsUseCase
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.application.report.GenerateCreditEvaluationReportUseCase
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller.CreditEvaluationReportController
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error.InvalidFilterException
import io.github.brdoliveira.creditflow.evaluation.infrastructure.report.PdfCreditEvaluationReportGenerator
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfCreditEvaluationReportTest {
    private val now = Instant.parse("2026-08-16T12:30:45Z")
    private val generator = PdfCreditEvaluationReportGenerator()

    @Test
    // @spec:AC-025
    fun `AC-025 controller returns a valid PDF attachment with a safe filename`() {
        val controller = controller(rows())
        val response = controller.report(null, null, null, emptyMap())

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_PDF, response.headers.contentType)
        assertEquals("attachment", response.headers.contentDisposition.type)
        assertEquals("credit-evaluations-20260816-123045.pdf", response.headers.contentDisposition.filename)
        assertTrue(response.body!!.take(4).toByteArray().contentEquals("%PDF".toByteArray()))
    }

    @Test
    // @spec:AC-026
    fun `AC-026 PDF exposes filters totals rates and auditable evaluation rows`() {
        val text = extractText(generator.generate(rows(), now, null, null, null))

        assertTrue(text.contains("Filtros: decisao=todas"))
        assertTrue(text.contains("Total: 2 | Aprovadas: 1 | Reprovadas: 1 | Taxa de aprovacao: 50.00%"))
        assertTrue(text.contains("***.***.***-09 | APPROVED | 1200.50"))
        assertTrue(text.contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
    }

    @Test
    // @spec:AC-027
    fun `AC-027 empty report is still a readable PDF with zero totals`() {
        val bytes = generator.generate(emptyList(), now, null, null, null)
        val text = extractText(bytes)

        assertTrue(bytes.take(4).toByteArray().contentEquals("%PDF".toByteArray()))
        assertTrue(text.contains("Total: 0 | Aprovadas: 0 | Reprovadas: 0 | Taxa de aprovacao: 0.00%"))
    }

    @Test
    // @spec:AC-028
    fun `AC-028 inverted periods and unknown filters are rejected with their cause`() {
        val controller = controller(emptyList())
        val inverted = assertThrows<InvalidFilterException> {
            controller.report(null, LocalDate.parse("2026-08-16"), LocalDate.parse("2026-08-01"), mapOf("from" to "x", "to" to "x"))
        }
        val unknown = assertThrows<InvalidFilterException> {
            controller.report(null, null, null, mapOf("format" to "html"))
        }
        assertTrue(inverted.message!!.contains("from must not be after to"))
        assertTrue(unknown.message!!.contains("Unknown filter: format"))
    }

    private fun controller(evaluations: List<CreditEvaluation>): CreditEvaluationReportController {
        val repository = FixedRepository(evaluations)
        val useCase = GenerateCreditEvaluationReportUseCase(ListCreditEvaluationsUseCase(repository), generator)
        return CreditEvaluationReportController(useCase, Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun rows() = listOf(
        evaluation("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "***.***.***-09", CreditDecisionStatus.APPROVED, BigDecimal("1200.50")),
        evaluation("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "***.***.***-10", CreditDecisionStatus.REJECTED, BigDecimal.ZERO),
    )

    private fun evaluation(id: String, cpf: String, decision: CreditDecisionStatus, amount: BigDecimal) = CreditEvaluation(
        UUID.fromString(id), cpf, decision, emptyList(), amount, "2026.08", now, 10, "correlation",
    )

    private fun extractText(bytes: ByteArray): String = Loader.loadPDF(bytes).use(PDFTextStripper()::getText)

    private class FixedRepository(private val evaluations: List<CreditEvaluation>) : CreditEvaluationRepository {
        override fun save(evaluation: CreditEvaluation) = evaluation
        override fun findById(evaluationId: UUID) = evaluations.find { it.evaluationId == evaluationId }
        override fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest) =
            CreditEvaluationPage(evaluations, evaluations.size.toLong(), page.page, page.size, page.sort)
    }
}
