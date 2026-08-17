package io.github.brdoliveira.creditflow.evaluation.infrastructure.report

import io.github.brdoliveira.creditflow.evaluation.application.report.CreditEvaluationReportGenerator
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Renderiza avaliações consolidadas no relatório PDF auditável. */
class PdfCreditEvaluationReportGenerator : CreditEvaluationReportGenerator {
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    /** Gera um PDF paginado com filtros, totais e avaliações ordenadas. */
    override fun generate(
        evaluations: List<CreditEvaluation>,
        generatedAt: Instant,
        decision: String?,
        from: Instant?,
        to: Instant?,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            var page = PDPage().also(document::addPage)
            var stream = PDPageContentStream(document, page)
            var y = TOP_MARGIN

            fun line(text: String, font: PDType1Font = regular, size: Float = BODY_FONT_SIZE) {
                if (y < BOTTOM_MARGIN) {
                    stream.close()
                    page = PDPage().also(document::addPage)
                    stream = PDPageContentStream(document, page)
                    y = TOP_MARGIN
                }
                stream.beginText()
                stream.setFont(font, size)
                stream.newLineAtOffset(LEFT_MARGIN, y)
                stream.showText(text.pdfSafe())
                stream.endText()
                y -= size + LINE_SPACING
            }

            val approved = evaluations.count { it.decision == CreditDecisionStatus.APPROVED }
            val rejected = evaluations.size - approved
            val approvalRate = if (evaluations.isEmpty()) BigDecimal.ZERO else {
                BigDecimal(approved).multiply(BigDecimal(100))
                    .divide(BigDecimal(evaluations.size), 2, RoundingMode.HALF_EVEN)
            }

            line("Relatorio de avaliacoes de credito rotativo", bold, TITLE_FONT_SIZE)
            line("Gerado em: ${DATE_TIME_FORMAT.format(generatedAt.atOffset(ZoneOffset.UTC))}")
            line("Filtros: decisao=${decision ?: "todas"}; de=${from ?: "inicio"}; ate=${to ?: "fim"}")
            line(
                "Total: ${evaluations.size} | Aprovadas: $approved | Reprovadas: $rejected | " +
                    "Taxa de aprovacao: ${approvalRate.setScale(2)}%",
                bold,
            )
            y -= SECTION_SPACING
            line("CPF mascarado | Decisao | Valor BRL | Data UTC | evaluationId", bold)
            evaluations.forEach { evaluation ->
                line(
                    "${evaluation.maskedCpf} | ${evaluation.decision} | " +
                        "${evaluation.approvedAmount.setScale(2, RoundingMode.HALF_EVEN)} | " +
                        "${DATE_TIME_FORMAT.format(evaluation.processedAt.atOffset(ZoneOffset.UTC))} | " +
                        evaluation.evaluationId,
                )
            }
            stream.close()
            document.save(output)
        }
        return output.toByteArray()
    }

    private fun String.pdfSafe(): String = map { if (it.code in 32..126) it else '?' }.joinToString("")

    private companion object {
        const val TOP_MARGIN = 750f
        const val BOTTOM_MARGIN = 55f
        const val LEFT_MARGIN = 45f
        const val BODY_FONT_SIZE = 9f
        const val TITLE_FONT_SIZE = 16f
        const val LINE_SPACING = 5f
        const val SECTION_SPACING = 8f
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
    }
}
