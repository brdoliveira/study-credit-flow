package io.github.brdoliveira.creditflow.evaluation.infrastructure.report

import io.github.brdoliveira.creditflow.evaluation.application.report.CreditEvaluationReportGenerator
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import java.util.Locale

/** Renderiza avaliações consolidadas no relatório PDF auditável. */
class PdfCreditEvaluationReportGenerator : CreditEvaluationReportGenerator {
    private val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val canvas = PdfCanvas(PAGE_WIDTH)

    /** Gera um PDF paginado com filtros, indicadores, tabela e rodapé. */
    override fun generate(
        evaluations: List<CreditEvaluation>,
        generatedAt: Instant,
        decision: String?,
        from: Instant?,
        to: Instant?,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val summary = ReportSummary.from(evaluations)
        PDDocument().use { document ->
            configureMetadata(document, generatedAt)
            var context = newPage(document, generatedAt, decision, from, to, summary, firstPage = true)

            if (evaluations.isEmpty()) {
                drawEmptyState(context.stream, context.rowTop)
            } else {
                evaluations.forEachIndexed { index, evaluation ->
                    if (context.rowTop - TABLE_ROW_HEIGHT < FOOTER_RESERVED_HEIGHT) {
                        context.stream.close()
                        context = newPage(document, generatedAt, decision, from, to, summary, firstPage = false)
                    }
                    drawTableRow(context.stream, context.rowTop, evaluation, index)
                    context.rowTop -= TABLE_ROW_HEIGHT
                }
            }
            context.stream.close()
            addFooters(document)
            document.save(output)
        }
        return output.toByteArray()
    }

    private fun configureMetadata(document: PDDocument, generatedAt: Instant) {
        document.documentInformation.apply {
            title = "Relatório de avaliações de crédito rotativo"
            subject = "Visão consolidada das decisões de crédito"
            author = "Study Credit Flow"
            creator = "Study Credit Flow"
            creationDate = GregorianCalendar.from(generatedAt.atZone(ZoneOffset.UTC))
        }
    }

    private fun newPage(
        document: PDDocument,
        generatedAt: Instant,
        decision: String?,
        from: Instant?,
        to: Instant?,
        summary: ReportSummary,
        firstPage: Boolean,
    ): PageContext {
        val page = PDPage(LANDSCAPE_A4)
        document.addPage(page)
        val stream = PDPageContentStream(document, page)
        drawHeader(stream, generatedAt)
        val tableTop = if (firstPage) {
            drawFilters(stream, decision, from, to)
            drawSummary(stream, summary)
            drawSectionTitle(stream, "Detalhamento das avaliações", FIRST_SECTION_TITLE_Y)
            FIRST_TABLE_TOP
        } else {
            drawSectionTitle(stream, "Detalhamento das avaliações - continuação", NEXT_SECTION_TITLE_Y)
            NEXT_TABLE_TOP
        }
        drawTableHeader(stream, tableTop)
        return PageContext(stream, tableTop - TABLE_HEADER_HEIGHT)
    }

    private fun drawHeader(stream: PDPageContentStream, generatedAt: Instant) {
        canvas.fillRect(stream, 0f, PAGE_HEIGHT - HEADER_HEIGHT, PAGE_WIDTH, HEADER_HEIGHT, HEADER_COLOR)
        canvas.fillRect(stream, 0f, PAGE_HEIGHT - HEADER_HEIGHT, HEADER_ACCENT_WIDTH, HEADER_HEIGHT, ACCENT_COLOR)
        canvas.drawText(stream, "CRÉDITO ROTATIVO", PAGE_MARGIN, PAGE_HEIGHT - 29f, bold, 8f, ACCENT_LIGHT)
        canvas.drawText(stream, "Relatório de avaliações de crédito", PAGE_MARGIN, PAGE_HEIGHT - 52f, bold, 19f, Color.WHITE)
        canvas.drawText(
            stream,
            "Visão consolidada para acompanhamento e auditoria de decisões",
            PAGE_MARGIN,
            PAGE_HEIGHT - 69f,
            regular,
            9f,
            HEADER_SECONDARY_TEXT,
        )
        canvas.drawRightText(stream, "GERADO EM", PAGE_WIDTH - PAGE_MARGIN, PAGE_HEIGHT - 34f, bold, 7f, ACCENT_LIGHT)
        canvas.drawRightText(
            stream,
            DATE_TIME_FORMAT.format(generatedAt.atOffset(ZoneOffset.UTC)),
            PAGE_WIDTH - PAGE_MARGIN,
            PAGE_HEIGHT - 52f,
            regular,
            9f,
            Color.WHITE,
        )
    }

    private fun drawFilters(stream: PDPageContentStream, decision: String?, from: Instant?, to: Instant?) {
        canvas.fillRect(stream, PAGE_MARGIN, FILTER_Y, CONTENT_WIDTH, FILTER_HEIGHT, FILTER_BACKGROUND)
        canvas.fillRect(stream, PAGE_MARGIN, FILTER_Y, 3f, FILTER_HEIGHT, ACCENT_COLOR)
        canvas.drawText(stream, "PERÍODO ANALISADO", PAGE_MARGIN + 14f, FILTER_Y + 21f, bold, 7f, MUTED_TEXT)
        canvas.drawText(stream, periodLabel(from, to), PAGE_MARGIN + 14f, FILTER_Y + 8f, regular, 9f, BODY_TEXT)
        canvas.drawText(stream, "DECISÃO", PAGE_MARGIN + 310f, FILTER_Y + 21f, bold, 7f, MUTED_TEXT)
        canvas.drawText(stream, decisionLabel(decision), PAGE_MARGIN + 310f, FILTER_Y + 8f, regular, 9f, BODY_TEXT)
        canvas.drawText(stream, "ORIGEM", PAGE_MARGIN + 540f, FILTER_Y + 21f, bold, 7f, MUTED_TEXT)
        canvas.drawText(stream, "Avaliações persistidas", PAGE_MARGIN + 540f, FILTER_Y + 8f, regular, 9f, BODY_TEXT)
    }

    private fun drawSummary(stream: PDPageContentStream, summary: ReportSummary) {
        val gap = 10f
        val width = (CONTENT_WIDTH - gap * 3) / 4
        val metrics = listOf(
            Metric("TOTAL DE AVALIAÇÕES", summary.total.toString(), ACCENT_COLOR),
            Metric("APROVADAS", summary.approved.toString(), APPROVED_COLOR),
            Metric("REPROVADAS", summary.rejected.toString(), REJECTED_COLOR),
            Metric("TAXA DE APROVAÇÃO", "${formatDecimal(summary.approvalRate)}%", RATE_COLOR),
        )
        metrics.forEachIndexed { index, metric ->
            val x = PAGE_MARGIN + index * (width + gap)
            canvas.fillRect(stream, x, SUMMARY_Y, width, SUMMARY_HEIGHT, SUMMARY_BACKGROUND)
            canvas.fillRect(stream, x, SUMMARY_Y + SUMMARY_HEIGHT - 3f, width, 3f, metric.color)
            canvas.drawText(stream, metric.label, x + 12f, SUMMARY_Y + 32f, bold, 7f, MUTED_TEXT)
            canvas.drawText(stream, metric.value, x + 12f, SUMMARY_Y + 11f, bold, 16f, BODY_TEXT)
        }
    }

    private fun drawSectionTitle(stream: PDPageContentStream, title: String, y: Float) {
        canvas.drawText(stream, title, PAGE_MARGIN, y, bold, 10f, BODY_TEXT)
        canvas.drawRightText(stream, "Valores monetários em BRL", PAGE_WIDTH - PAGE_MARGIN, y, regular, 7.5f, MUTED_TEXT)
    }

    private fun drawTableHeader(stream: PDPageContentStream, top: Float) {
        canvas.fillRect(stream, PAGE_MARGIN, top - TABLE_HEADER_HEIGHT, CONTENT_WIDTH, TABLE_HEADER_HEIGHT, TABLE_HEADER_COLOR)
        var x = PAGE_MARGIN
        TABLE_COLUMNS.forEach { column ->
            canvas.drawText(stream, column.label, x + CELL_PADDING, top - 16f, bold, 7.2f, Color.WHITE, column.width - CELL_PADDING * 2)
            x += column.width
        }
    }

    private fun drawTableRow(
        stream: PDPageContentStream,
        top: Float,
        evaluation: CreditEvaluation,
        index: Int,
    ) {
        val bottom = top - TABLE_ROW_HEIGHT
        canvas.fillRect(stream, PAGE_MARGIN, bottom, CONTENT_WIDTH, TABLE_ROW_HEIGHT, if (index % 2 == 0) Color.WHITE else ROW_ALTERNATE)
        val decisionColumnX = PAGE_MARGIN + TABLE_COLUMNS.first().width
        canvas.fillRect(
            stream,
            decisionColumnX,
            bottom,
            TABLE_COLUMNS[1].width,
            TABLE_ROW_HEIGHT,
            if (evaluation.decision == CreditDecisionStatus.APPROVED) APPROVED_BACKGROUND else REJECTED_BACKGROUND,
        )
        canvas.drawHorizontalLine(stream, PAGE_MARGIN, PAGE_WIDTH - PAGE_MARGIN, bottom, TABLE_LINE_COLOR)

        val values = listOf(
            evaluation.maskedCpf,
            if (evaluation.decision == CreditDecisionStatus.APPROVED) "Aprovada" else "Reprovada",
            formatCurrency(evaluation.approvedAmount),
            ROW_DATE_TIME_FORMAT.format(evaluation.processedAt.atOffset(ZoneOffset.UTC)),
            evaluation.evaluationId.toString(),
        )
        var x = PAGE_MARGIN
        TABLE_COLUMNS.forEachIndexed { columnIndex, column ->
            val color = when (columnIndex) {
                1 -> if (evaluation.decision == CreditDecisionStatus.APPROVED) APPROVED_TEXT else REJECTED_TEXT
                else -> BODY_TEXT
            }
            if (column.alignment == Alignment.RIGHT) {
                canvas.drawRightText(stream, values[columnIndex], x + column.width - CELL_PADDING, bottom + 7f, regular, 8.5f, color)
            } else {
                canvas.drawText(stream, values[columnIndex], x + CELL_PADDING, bottom + 7f, regular, 8.5f, color, column.width - CELL_PADDING * 2)
            }
            x += column.width
        }
    }

    private fun drawEmptyState(stream: PDPageContentStream, rowTop: Float) {
        val height = 64f
        val bottom = rowTop - height
        canvas.fillRect(stream, PAGE_MARGIN, bottom, CONTENT_WIDTH, height, FILTER_BACKGROUND)
        canvas.drawCenteredText(stream, "Nenhuma avaliação encontrada para os filtros informados", bottom + 35f, bold, 11f, BODY_TEXT)
        canvas.drawCenteredText(stream, "O relatório permanece válido e apresenta os indicadores consolidados com valor zero.", bottom + 17f, regular, 8.5f, MUTED_TEXT)
    }

    private fun addFooters(document: PDDocument) {
        val totalPages = document.numberOfPages
        document.pages.forEachIndexed { index, page ->
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                canvas.drawHorizontalLine(stream, PAGE_MARGIN, PAGE_WIDTH - PAGE_MARGIN, FOOTER_LINE_Y, TABLE_LINE_COLOR)
                canvas.drawText(
                    stream,
                    "Uso interno | CPFs mascarados para proteção de dados",
                    PAGE_MARGIN,
                    FOOTER_TEXT_Y,
                    regular,
                    7f,
                    MUTED_TEXT,
                )
                canvas.drawRightText(
                    stream,
                    "Página ${index + 1} de $totalPages",
                    PAGE_WIDTH - PAGE_MARGIN,
                    FOOTER_TEXT_Y,
                    bold,
                    7f,
                    MUTED_TEXT,
                )
            }
        }
    }

    private fun periodLabel(from: Instant?, to: Instant?): String {
        val start = from?.let(DATE_FORMAT::format) ?: "início"
        val end = to?.let(DATE_FORMAT::format) ?: "data de geração"
        return "$start a $end"
    }

    private fun decisionLabel(decision: String?): String = when (decision) {
        CreditDecisionStatus.APPROVED.name -> "Somente aprovadas"
        CreditDecisionStatus.REJECTED.name -> "Somente reprovadas"
        else -> "Todas as decisões"
    }

    private fun formatCurrency(value: BigDecimal): String = "R$ ${String.format(PT_BR, "%,.2f", value)}"

    private fun formatDecimal(value: BigDecimal): String = String.format(PT_BR, "%,.2f", value)

    private data class PageContext(val stream: PDPageContentStream, var rowTop: Float)
    private data class Metric(val label: String, val value: String, val color: Color)
    private data class Column(val label: String, val width: Float, val alignment: Alignment = Alignment.LEFT)
    private enum class Alignment { LEFT, RIGHT }

    private data class ReportSummary(
        val total: Int,
        val approved: Int,
        val rejected: Int,
        val approvalRate: BigDecimal,
    ) {
        companion object {
            fun from(evaluations: List<CreditEvaluation>): ReportSummary {
                val approved = evaluations.count { it.decision == CreditDecisionStatus.APPROVED }
                val rate = if (evaluations.isEmpty()) {
                    BigDecimal.ZERO.setScale(2)
                } else {
                    BigDecimal(approved).multiply(BigDecimal(100))
                        .divide(BigDecimal(evaluations.size), 2, RoundingMode.HALF_EVEN)
                }
                return ReportSummary(evaluations.size, approved, evaluations.size - approved, rate)
            }
        }
    }

    private companion object {
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        val LANDSCAPE_A4 = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
        val PAGE_WIDTH: Float = LANDSCAPE_A4.width
        val PAGE_HEIGHT: Float = LANDSCAPE_A4.height
        const val PAGE_MARGIN = 36f
        val CONTENT_WIDTH: Float = PAGE_WIDTH - PAGE_MARGIN * 2

        const val HEADER_HEIGHT = 82f
        const val HEADER_ACCENT_WIDTH = 6f
        const val FILTER_Y = 465f
        const val FILTER_HEIGHT = 34f
        const val SUMMARY_Y = 395f
        const val SUMMARY_HEIGHT = 54f
        const val FIRST_SECTION_TITLE_Y = 373f
        const val FIRST_TABLE_TOP = 359f
        const val NEXT_SECTION_TITLE_Y = 484f
        const val NEXT_TABLE_TOP = 470f
        const val TABLE_HEADER_HEIGHT = 24f
        const val TABLE_ROW_HEIGHT = 22f
        const val CELL_PADDING = 8f
        const val FOOTER_RESERVED_HEIGHT = 42f
        const val FOOTER_LINE_Y = 34f
        const val FOOTER_TEXT_Y = 20f

        val TABLE_COLUMNS = listOf(
            Column("CPF MASCARADO", 118f),
            Column("DECISÃO", 92f),
            Column("VALOR APROVADO", 120f, Alignment.RIGHT),
            Column("DATA (UTC)", 128f),
            Column("IDENTIFICADOR DA AVALIAÇÃO", CONTENT_WIDTH - 458f),
        )

        val HEADER_COLOR = Color(31, 41, 55)
        val ACCENT_COLOR = Color(13, 148, 136)
        val ACCENT_LIGHT = Color(153, 246, 228)
        val HEADER_SECONDARY_TEXT = Color(209, 213, 219)
        val BODY_TEXT = Color(31, 41, 55)
        val MUTED_TEXT = Color(100, 116, 139)
        val FILTER_BACKGROUND = Color(241, 245, 249)
        val SUMMARY_BACKGROUND = Color(248, 250, 252)
        val TABLE_HEADER_COLOR = Color(51, 65, 85)
        val ROW_ALTERNATE = Color(248, 250, 252)
        val TABLE_LINE_COLOR = Color(226, 232, 240)
        val APPROVED_COLOR = Color(22, 163, 74)
        val REJECTED_COLOR = Color(220, 38, 38)
        val RATE_COLOR = Color(37, 99, 235)
        val APPROVED_BACKGROUND = Color(240, 253, 244)
        val REJECTED_BACKGROUND = Color(254, 242, 242)
        val APPROVED_TEXT = Color(21, 128, 61)
        val REJECTED_TEXT = Color(185, 28, 28)

        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'")
        val ROW_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC)
    }
}

private class PdfCanvas(private val pageWidth: Float) {
    /** Preenche uma area retangular com a cor informada. */
    fun fillRect(stream: PDPageContentStream, x: Float, y: Float, width: Float, height: Float, color: Color) {
        stream.setNonStrokingColor(color)
        stream.addRect(x, y, width, height)
        stream.fill()
    }

    /** Desenha uma linha horizontal para separar secoes do documento. */
    fun drawHorizontalLine(stream: PDPageContentStream, fromX: Float, toX: Float, y: Float, color: Color) {
        stream.setStrokingColor(color)
        stream.setLineWidth(0.5f)
        stream.moveTo(fromX, y)
        stream.lineTo(toX, y)
        stream.stroke()
    }

    /** Escreve texto no documento, limitando sua largura quando necessario. */
    fun drawText(
        stream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        font: PDFont,
        size: Float,
        color: Color,
        maxWidth: Float? = null,
    ) {
        val fitted = maxWidth?.let { fitText(text, font, size, it) } ?: safeText(text, font)
        stream.beginText()
        stream.setNonStrokingColor(color)
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(fitted)
        stream.endText()
    }

    /** Escreve texto alinhado pela margem direita. */
    fun drawRightText(
        stream: PDPageContentStream,
        text: String,
        right: Float,
        y: Float,
        font: PDFont,
        size: Float,
        color: Color,
    ) {
        val safe = safeText(text, font)
        drawText(stream, safe, right - textWidth(safe, font, size), y, font, size, color)
    }

    /** Escreve texto centralizado na largura da pagina. */
    fun drawCenteredText(
        stream: PDPageContentStream,
        text: String,
        y: Float,
        font: PDFont,
        size: Float,
        color: Color,
    ) {
        val safe = safeText(text, font)
        drawText(stream, safe, (pageWidth - textWidth(safe, font, size)) / 2, y, font, size, color)
    }

    private fun fitText(text: String, font: PDFont, size: Float, maxWidth: Float): String {
        val safe = safeText(text, font)
        if (textWidth(safe, font, size) <= maxWidth) return safe
        var fitted = safe
        while (fitted.isNotEmpty() && textWidth("$fitted...", font, size) > maxWidth) fitted = fitted.dropLast(1)
        return "$fitted..."
    }

    private fun safeText(text: String, font: PDFont): String = buildString {
        text.forEach { character ->
            val normalized = if (character.isWhitespace()) ' ' else character
            append(if (runCatching { font.encode(normalized.toString()) }.isSuccess) normalized else '?')
        }
    }

    private fun textWidth(text: String, font: PDFont, size: Float): Float = font.getStringWidth(text) / 1000f * size
}
