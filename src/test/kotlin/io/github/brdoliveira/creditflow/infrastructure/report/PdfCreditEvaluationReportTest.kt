package io.github.brdoliveira.creditflow.infrastructure.report

import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReport
import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReportFilter
import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReportRow
import io.github.brdoliveira.creditflow.infrastructure.web.CreditEvaluationReportController
import io.github.brdoliveira.creditflow.infrastructure.web.CreditEvaluationReportService
import io.github.brdoliveira.creditflow.infrastructure.web.InvalidFilterException
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
        val controller = CreditEvaluationReportController(
            CreditEvaluationReportService { _, _, _ -> generator.generate(report(rows())) },
            Clock.fixed(now, ZoneOffset.UTC),
        )
        val response = controller.report(null, null, null, emptyMap(), "correlation")

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_PDF, response.headers.contentType)
        assertEquals("attachment", response.headers.contentDisposition.type)
        assertEquals("credit-evaluations-20260816-123045.pdf", response.headers.contentDisposition.filename)
        assertTrue(response.body!!.take(4).toByteArray().contentEquals("%PDF".toByteArray()))
    }

    @Test
    // @spec:AC-026
    fun `AC-026 PDF exposes filters totals rates and auditable evaluation rows`() {
        val text = extractText(generator.generate(report(rows())))

        assertTrue(text.contains("Filtros: decisao=todas"))
        assertTrue(text.contains("Total: 2 | Aprovadas: 1 | Reprovadas: 1 | Taxa de aprovacao: 50.00%"))
        assertTrue(text.contains("***.***.***-09 | APPROVED | 1200.50"))
        assertTrue(text.contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
    }

    @Test
    // @spec:AC-027
    fun `AC-027 empty report is still a readable PDF with zero totals`() {
        val bytes = generator.generate(report(emptyList()))
        val text = extractText(bytes)

        assertTrue(bytes.take(4).toByteArray().contentEquals("%PDF".toByteArray()))
        assertTrue(text.contains("Total: 0 | Aprovadas: 0 | Reprovadas: 0 | Taxa de aprovacao: 0.00%"))
    }

    @Test
    // @spec:AC-028
    fun `AC-028 inverted periods and unknown filters are rejected with their cause`() {
        val controller = CreditEvaluationReportController(CreditEvaluationReportService { _, _, _ -> byteArrayOf() })

        val inverted = assertThrows<InvalidFilterException> {
            controller.report(null, LocalDate.parse("2026-08-16"), LocalDate.parse("2026-08-01"), setOf("from", "to").associateWith { "x" }, null)
        }
        val unknown = assertThrows<InvalidFilterException> {
            controller.report(null, null, null, mapOf("format" to "html"), null)
        }
        assertTrue(inverted.message!!.contains("from must not be after to"))
        assertTrue(unknown.message!!.contains("Unknown filter: format"))
    }

    private fun report(rows: List<CreditEvaluationReportRow>) = CreditEvaluationReport(
        CreditEvaluationReportFilter(),
        now,
        rows,
    )

    private fun rows() = listOf(
        CreditEvaluationReportRow(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "***.***.***-09", "APPROVED", BigDecimal("1200.50"), now),
        CreditEvaluationReportRow(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "***.***.***-10", "REJECTED", BigDecimal.ZERO, now),
    )

    private fun extractText(bytes: ByteArray): String = Loader.loadPDF(bytes).use(PDFTextStripper()::getText)
}
