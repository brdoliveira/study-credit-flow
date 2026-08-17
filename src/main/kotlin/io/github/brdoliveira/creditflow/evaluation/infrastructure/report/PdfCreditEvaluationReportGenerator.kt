package io.github.brdoliveira.creditflow.evaluation.infrastructure.report

import io.github.brdoliveira.creditflow.evaluation.application.report.CreditEvaluationReportGenerator
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.font.*
import java.io.ByteArrayOutputStream
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Renderiza avaliações consolidadas no PDF compatível com o relatório existente. */
class PdfCreditEvaluationReportGenerator : CreditEvaluationReportGenerator {
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA); private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    /** Gera um PDF textual com resumo, filtros e linhas ordenadas. */
    override fun generate(evaluations: List<CreditEvaluation>, generatedAt: Instant, decision: String?, from: Instant?, to: Instant?): ByteArray {
        val output = ByteArrayOutputStream(); PDDocument().use { document -> document.addPage(PDPage()); PDPageContentStream(document, document.getPage(0)).use { stream -> var y = 750f
            fun line(value: String, font: PDFont = regular, size: Float = 9f) { stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(45f, y); stream.showText(value.map { if (it.code in 32..126) it else '?' }.joinToString("")); stream.endText(); y -= size + 5f }
            val approved = evaluations.count { it.decision.status.name == "APPROVED" }; line("Relatorio de avaliacoes de credito rotativo", bold, 16f); line("Gerado em: ${FORMAT.format(generatedAt.atOffset(ZoneOffset.UTC))}"); line("Filtros: decisao=${decision ?: "todas"}; de=${from ?: "inicio"}; ate=${to ?: "fim"}"); line("Total: ${evaluations.size} | Aprovadas: $approved | Reprovadas: ${evaluations.size - approved}", bold); line("CPF mascarado | Decisao | Valor BRL | Data UTC | evaluationId", bold); evaluations.forEach { line("${it.maskedCpf} | ${it.decision.status} | ${it.approvedAmount.setScale(2, RoundingMode.HALF_EVEN)} | ${FORMAT.format(it.processedAt.atOffset(ZoneOffset.UTC))} | ${it.evaluationId}") }
        }; document.save(output) }; return output.toByteArray()
    }
    private companion object { val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'") }
}
