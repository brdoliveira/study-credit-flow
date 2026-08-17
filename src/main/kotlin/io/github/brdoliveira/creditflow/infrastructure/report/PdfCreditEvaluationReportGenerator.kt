package io.github.brdoliveira.creditflow.infrastructure.report

import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReport
import io.github.brdoliveira.creditflow.application.report.CreditEvaluationReportGenerator
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class PdfCreditEvaluationReportGenerator : CreditEvaluationReportGenerator {
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    override fun generate(report: CreditEvaluationReport): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            var page = PDPage()
            document.addPage(page)
            var stream = PDPageContentStream(document, page)
            var y = 750f

            fun line(text: String, font: PDType1Font = regular, size: Float = 9f) {
                if (y < 55f) {
                    stream.close()
                    page = PDPage()
                    document.addPage(page)
                    stream = PDPageContentStream(document, page)
                    y = 750f
                }
                stream.beginText()
                stream.setFont(font, size)
                stream.newLineAtOffset(45f, y)
                stream.showText(text.pdfSafe())
                stream.endText()
                y -= size + 5f
            }

            val approved = report.evaluations.count { it.decision == "APPROVED" }
            val rejected = report.evaluations.size - approved
            val approvalRate = if (report.evaluations.isEmpty()) BigDecimal.ZERO else
                BigDecimal(approved).multiply(BigDecimal("100"))
                    .divide(BigDecimal(report.evaluations.size), 2, RoundingMode.HALF_EVEN)

            line("Relatorio de avaliacoes de credito rotativo", bold, 16f)
            line("Gerado em: ${DATE_TIME_FORMAT.format(report.generatedAt.atOffset(ZoneOffset.UTC))}")
            line("Filtros: decisao=${report.filter.decision ?: "todas"}; de=${report.filter.from ?: "inicio"}; ate=${report.filter.to ?: "fim"}")
            line("Total: ${report.evaluations.size} | Aprovadas: $approved | Reprovadas: $rejected | Taxa de aprovacao: ${approvalRate.setScale(2)}%", bold)
            y -= 8f
            line("CPF mascarado | Decisao | Valor BRL | Data UTC | evaluationId", bold)
            report.evaluations.forEach { evaluation ->
                line(
                    "${evaluation.maskedCpf} | ${evaluation.decision} | ${evaluation.approvedAmount.setScale(2, RoundingMode.HALF_EVEN)} | " +
                        "${DATE_TIME_FORMAT.format(evaluation.processedAt.atOffset(ZoneOffset.UTC))} | ${evaluation.evaluationId}"
                )
            }
            stream.close()
            document.save(output)
        }
        return output.toByteArray()
    }

    private fun String.pdfSafe(): String = map { if (it.code in 32..126) it else '?' }.joinToString("")

    companion object {
        private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
    }
}
