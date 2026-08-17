package io.github.brdoliveira.creditflow.platform.report

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import java.util.Locale

/** Renderiza uma evidência de teste de carga em um relatório PDF executivo e auditável. */
@Suppress("TooManyFunctions")
class LoadTestPdfReportGenerator {
    private val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val canvas = LoadTestPdfCanvas(PAGE_WIDTH)

    /** Gera o documento PDF a partir do resumo JSON produzido pelo k6. */
    fun generate(evidencePath: Path): ByteArray {
        val report = readReport(evidencePath)
        val output = ByteArrayOutputStream()

        PDDocument().use { document ->
            configureMetadata(document, report)
            val page = PDPage(LANDSCAPE_A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                drawHeader(stream, report)
                drawExecutionMetadata(stream, report)
                drawMetrics(stream, report)
                drawComparisonChart(stream, report)
                drawConfiguration(stream, report)
                drawThresholdTable(stream, report)
                drawFooter(stream, report)
            }
            document.save(output)
        }
        return output.toByteArray()
    }

    private fun readReport(path: Path): LoadTestReport {
        require(Files.isRegularFile(path)) { "Evidência k6 não encontrada: $path" }
        val root = ObjectMapper().readTree(Files.readString(path))
        val configuration = root.get("configuration")
        val observed = root.get("observed")
        val thresholdNodes = root.get("thresholds")
        val thresholds = buildList<ThresholdResult> {
            if (thresholdNodes != null && thresholdNodes.isArray) {
                thresholdNodes.forEach { threshold: JsonNode ->
                    add(
                        ThresholdResult(
                            metric = threshold.text("metric", "Métrica não informada"),
                            criterion = threshold.text("threshold", "Não informado"),
                            passed = threshold.boolean("passed"),
                        ),
                    )
                }
            }
        }

        return LoadTestReport(
            executionStatus = root.text("executionStatus", "unknown"),
            commit = root.text("commit", "unknown"),
            executedAtUtc = root.text("executedAtUtc", "unknown"),
            environment = root.text("environment", "unspecified"),
            resources = root.text("resources", "unspecified"),
            baseUrl = configuration.text("baseUrl", "unspecified"),
            nominalTarget = configuration.number("nominalRatePerMinute", 10_000.0),
            nominalDuration = configuration.text("nominalDuration", "5m"),
            warmUpTarget = configuration.number("warmUpRatePerMinute", 1_000.0),
            warmUpDuration = configuration.text("warmUpDuration", "1m"),
            nominalRate = observed.number("nominalRatePerMinute"),
            p99Milliseconds = observed.number("p99Milliseconds"),
            technicalErrorRate = observed.number("technicalErrorRate"),
            droppedIterations = observed.number("droppedIterations"),
            completedEvaluations = observed.number("completedEvaluations"),
            thresholds = thresholds,
            passed = root.boolean("passed"),
        )
    }

    private fun configureMetadata(document: PDDocument, report: LoadTestReport) {
        document.documentInformation.apply {
            title = "Relatório de desempenho - avaliação de crédito"
            subject = "Resultado auditável do cenário k6 de avaliação de crédito"
            author = "Study Credit Flow"
            creator = "Study Credit Flow"
            creationDate = runCatching {
                GregorianCalendar.from(Instant.parse(report.executedAtUtc).atZone(ZoneOffset.UTC))
            }.getOrNull()
        }
    }

    private fun drawHeader(stream: PDPageContentStream, report: LoadTestReport) {
        canvas.fillRect(stream, 0f, PAGE_HEIGHT - HEADER_HEIGHT, PAGE_WIDTH, HEADER_HEIGHT, HEADER_COLOR)
        canvas.fillRect(stream, 0f, PAGE_HEIGHT - HEADER_HEIGHT, 6f, HEADER_HEIGHT, ACCENT_COLOR)
        canvas.text(stream, "STUDY CREDIT FLOW  |  PERFORMANCE", PAGE_MARGIN, PAGE_HEIGHT - 27f, bold, 7.5f, ACCENT_LIGHT)
        canvas.text(stream, "Relatório de teste de carga", PAGE_MARGIN, PAGE_HEIGHT - 51f, bold, 19f, Color.WHITE)
        canvas.text(
            stream,
            "Cenário nominal de avaliações de crédito com evidência rastreável",
            PAGE_MARGIN,
            PAGE_HEIGHT - 68f,
            regular,
            8.5f,
            HEADER_SECONDARY,
        )

        val statusColor = if (report.passed) PASSED_COLOR else FAILED_COLOR
        val statusText = if (report.passed) "APROVADO" else "REPROVADO"
        canvas.fillRect(stream, PAGE_WIDTH - PAGE_MARGIN - 94f, PAGE_HEIGHT - 51f, 94f, 24f, statusColor)
        canvas.centeredText(stream, statusText, PAGE_WIDTH - PAGE_MARGIN - 47f, PAGE_HEIGHT - 44f, bold, 9f, Color.WHITE)
        canvas.rightText(
            stream,
            formatDateTime(report.executedAtUtc),
            PAGE_WIDTH - PAGE_MARGIN,
            PAGE_HEIGHT - 68f,
            regular,
            7.5f,
            HEADER_SECONDARY,
        )
    }

    private fun drawExecutionMetadata(stream: PDPageContentStream, report: LoadTestReport) {
        canvas.fillRect(stream, PAGE_MARGIN, METADATA_Y, CONTENT_WIDTH, 30f, PANEL_BACKGROUND)
        canvas.fillRect(stream, PAGE_MARGIN, METADATA_Y, 3f, 30f, ACCENT_COLOR)
        metadataItem(stream, "AMBIENTE", report.environment, PAGE_MARGIN + 14f, 170f)
        metadataItem(stream, "ENDPOINT", report.baseUrl, PAGE_MARGIN + 210f, 210f)
        metadataItem(stream, "COMMIT", shortCommit(report.commit), PAGE_MARGIN + 445f, 120f)
        metadataItem(stream, "EXECUÇÃO", report.executionStatus.uppercase(PT_BR), PAGE_MARGIN + 590f, 150f)
    }

    private fun metadataItem(stream: PDPageContentStream, label: String, value: String, x: Float, width: Float) {
        canvas.text(stream, label, x, METADATA_Y + 18f, bold, 6.5f, MUTED_TEXT)
        canvas.text(stream, value, x, METADATA_Y + 7f, regular, 8f, BODY_TEXT, width)
    }

    private fun drawMetrics(stream: PDPageContentStream, report: LoadTestReport) {
        val gap = 10f
        val width = (CONTENT_WIDTH - gap * 3) / 4f
        val metrics = listOf(
            MetricCard(
                "VAZÃO NOMINAL",
                "${formatNumber(report.nominalRate, 1)} / min",
                "Meta: >= ${formatNumber(report.nominalTarget, 0)} / min",
                report.nominalRate >= report.nominalTarget,
            ),
            MetricCard(
                "LATÊNCIA P99",
                "${formatNumber(report.p99Milliseconds, 1)} ms",
                "Limite: < 1.000 ms",
                report.p99Milliseconds < P99_LIMIT,
            ),
            MetricCard(
                "ERROS TÉCNICOS",
                formatPercentage(report.technicalErrorRate),
                "Limite: < 1,00%",
                report.technicalErrorRate < ERROR_RATE_LIMIT,
            ),
            MetricCard(
                "AVALIAÇÕES CONCLUÍDAS",
                formatNumber(report.completedEvaluations, 0),
                "Descartadas: ${formatNumber(report.droppedIterations, 0)}",
                report.droppedIterations == 0.0,
            ),
        )

        metrics.forEachIndexed { index, metric ->
            val x = PAGE_MARGIN + index * (width + gap)
            val color = if (metric.passed) PASSED_COLOR else FAILED_COLOR
            canvas.fillRect(stream, x, METRICS_Y, width, 56f, Color.WHITE)
            canvas.strokeRect(stream, x, METRICS_Y, width, 56f, BORDER_COLOR)
            canvas.fillRect(stream, x, METRICS_Y + 53f, width, 3f, color)
            canvas.text(stream, metric.label, x + 11f, METRICS_Y + 38f, bold, 6.8f, MUTED_TEXT)
            canvas.text(stream, metric.value, x + 11f, METRICS_Y + 18f, bold, 15f, BODY_TEXT, width - 22f)
            canvas.text(stream, metric.context, x + 11f, METRICS_Y + 7f, regular, 7f, MUTED_TEXT, width - 22f)
        }
    }

    private fun drawComparisonChart(stream: PDPageContentStream, report: LoadTestReport) {
        canvas.text(stream, "Observado x limite", PAGE_MARGIN, SECTION_TITLE_Y, bold, 10f, BODY_TEXT)
        canvas.text(
            stream,
            "A linha vertical marca 100% da meta ou do limite de cada indicador.",
            PAGE_MARGIN + 112f,
            SECTION_TITLE_Y,
            regular,
            7f,
            MUTED_TEXT,
        )
        canvas.fillRect(stream, PAGE_MARGIN, CHART_Y, CHART_WIDTH, CHART_HEIGHT, Color.WHITE)
        canvas.strokeRect(stream, PAGE_MARGIN, CHART_Y, CHART_WIDTH, CHART_HEIGHT, BORDER_COLOR)

        val graphMetrics = listOf(
            GraphMetric(
                "Vazão nominal (mínimo)",
                report.nominalRate / report.nominalTarget,
                "${formatNumber(report.nominalRate, 1)} / ${formatNumber(report.nominalTarget, 0)} por min",
                report.nominalRate >= report.nominalTarget,
            ),
            GraphMetric(
                "Latência p99 (máximo)",
                report.p99Milliseconds / P99_LIMIT,
                "${formatNumber(report.p99Milliseconds, 1)} / 1.000 ms",
                report.p99Milliseconds < P99_LIMIT,
            ),
            GraphMetric(
                "Erros técnicos (máximo)",
                report.technicalErrorRate / ERROR_RATE_LIMIT,
                "${formatPercentage(report.technicalErrorRate)} / 1,00%",
                report.technicalErrorRate < ERROR_RATE_LIMIT,
            ),
            GraphMetric(
                "Iterações descartadas (= 0)",
                if (report.droppedIterations == 0.0) 0.0 else 1.2,
                formatNumber(report.droppedIterations, 0),
                report.droppedIterations == 0.0,
            ),
        )

        val labelX = PAGE_MARGIN + 14f
        val trackX = PAGE_MARGIN + 160f
        val trackWidth = CHART_WIDTH - 178f
        val targetX = trackX + trackWidth / GRAPH_SCALE
        graphMetrics.forEachIndexed { index, metric ->
            val y = CHART_Y + CHART_HEIGHT - 31f - index * 29f
            val fillWidth = (trackWidth * (metric.ratio / GRAPH_SCALE).coerceIn(0.0, 1.0)).toFloat()
            val color = if (metric.passed) PASSED_COLOR else FAILED_COLOR
            canvas.text(stream, metric.label, labelX, y + 4f, bold, 7f, BODY_TEXT, 138f)
            canvas.fillRect(stream, trackX, y, trackWidth, 9f, TRACK_COLOR)
            if (fillWidth > 0f) canvas.fillRect(stream, trackX, y, fillWidth.coerceAtLeast(2f), 9f, color)
            canvas.verticalLine(stream, targetX, y - 2f, y + 12f, TARGET_COLOR, 1.2f)
            canvas.rightText(stream, metric.value, trackX + trackWidth, y + 13f, regular, 6.5f, MUTED_TEXT)
        }

        canvas.text(stream, "0%", trackX, CHART_Y + 8f, regular, 6f, MUTED_TEXT)
        canvas.centeredText(stream, "100%", targetX, CHART_Y + 8f, bold, 6f, TARGET_COLOR)
        canvas.rightText(stream, "120%+", trackX + trackWidth, CHART_Y + 8f, regular, 6f, MUTED_TEXT)
    }

    private fun drawConfiguration(stream: PDPageContentStream, report: LoadTestReport) {
        val x = PAGE_MARGIN + CHART_WIDTH + 12f
        val width = CONTENT_WIDTH - CHART_WIDTH - 12f
        canvas.text(stream, "Configuração da execução", x, SECTION_TITLE_Y, bold, 10f, BODY_TEXT)
        canvas.fillRect(stream, x, CHART_Y, width, CHART_HEIGHT, PANEL_BACKGROUND)
        canvas.fillRect(stream, x, CHART_Y, 3f, CHART_HEIGHT, ACCENT_COLOR)

        configurationRow(stream, x, width, CHART_Y + 119f, "AQUECIMENTO", "${formatNumber(report.warmUpTarget, 0)}/min por ${report.warmUpDuration}")
        configurationRow(stream, x, width, CHART_Y + 88f, "FASE NOMINAL", "${formatNumber(report.nominalTarget, 0)}/min por ${report.nominalDuration}")
        configurationRow(stream, x, width, CHART_Y + 57f, "RECURSOS", report.resources)
        configurationRow(stream, x, width, CHART_Y + 26f, "RESULTADO", if (report.passed) "Todos os critérios atendidos" else "Um ou mais critérios não atendidos")
    }

    private fun configurationRow(stream: PDPageContentStream, x: Float, width: Float, y: Float, label: String, value: String) {
        canvas.text(stream, label, x + 14f, y + 8f, bold, 6.5f, MUTED_TEXT)
        canvas.text(stream, value, x + 14f, y - 3f, regular, 7.5f, BODY_TEXT, width - 28f)
    }

    private fun drawThresholdTable(stream: PDPageContentStream, report: LoadTestReport) {
        canvas.text(stream, "Critérios de aprovação", PAGE_MARGIN, TABLE_TITLE_Y, bold, 10f, BODY_TEXT)
        canvas.rightText(
            stream,
            "${report.thresholds.count { it.passed }} de ${report.thresholds.size} critérios atendidos",
            PAGE_WIDTH - PAGE_MARGIN,
            TABLE_TITLE_Y,
            regular,
            7f,
            MUTED_TEXT,
        )

        val top = TABLE_TOP
        canvas.fillRect(stream, PAGE_MARGIN, top - 20f, CONTENT_WIDTH, 20f, TABLE_HEADER_COLOR)
        canvas.text(stream, "MÉTRICA", PAGE_MARGIN + 8f, top - 14f, bold, 6.8f, Color.WHITE)
        canvas.text(stream, "CRITÉRIO", PAGE_MARGIN + 390f, top - 14f, bold, 6.8f, Color.WHITE)
        canvas.text(stream, "RESULTADO", PAGE_MARGIN + 645f, top - 14f, bold, 6.8f, Color.WHITE)

        report.thresholds.take(MAX_THRESHOLD_ROWS).forEachIndexed { index, threshold ->
            val rowTop = top - 20f - index * 17f
            val bottom = rowTop - 17f
            val background = if (index % 2 == 0) Color.WHITE else PANEL_BACKGROUND
            val resultColor = if (threshold.passed) PASSED_TEXT else FAILED_TEXT
            canvas.fillRect(stream, PAGE_MARGIN, bottom, CONTENT_WIDTH, 17f, background)
            canvas.horizontalLine(stream, PAGE_MARGIN, PAGE_WIDTH - PAGE_MARGIN, bottom, BORDER_COLOR)
            canvas.text(stream, threshold.metric, PAGE_MARGIN + 8f, bottom + 5f, regular, 6.8f, BODY_TEXT, 365f)
            canvas.text(stream, threshold.criterion, PAGE_MARGIN + 390f, bottom + 5f, regular, 6.8f, BODY_TEXT, 235f)
            canvas.text(
                stream,
                if (threshold.passed) "ATENDIDO" else "NÃO ATENDIDO",
                PAGE_MARGIN + 645f,
                bottom + 5f,
                bold,
                6.8f,
                resultColor,
            )
        }
    }

    private fun drawFooter(stream: PDPageContentStream, report: LoadTestReport) {
        canvas.horizontalLine(stream, PAGE_MARGIN, PAGE_WIDTH - PAGE_MARGIN, FOOTER_LINE_Y, BORDER_COLOR)
        canvas.text(
            stream,
            "Evidência de capacidade | Erro técnico: transporte ou HTTP 5xx",
            PAGE_MARGIN,
            FOOTER_TEXT_Y,
            regular,
            6.8f,
            MUTED_TEXT,
        )
        canvas.centeredText(stream, "Commit ${shortCommit(report.commit)}", PAGE_WIDTH / 2f, FOOTER_TEXT_Y, regular, 6.8f, MUTED_TEXT)
        canvas.rightText(stream, "Página 1 de 1", PAGE_WIDTH - PAGE_MARGIN, FOOTER_TEXT_Y, bold, 6.8f, MUTED_TEXT)
    }

    private fun formatDateTime(value: String): String = runCatching {
        DATE_TIME_FORMAT.format(Instant.parse(value).atOffset(ZoneOffset.UTC))
    }.getOrElse { value }

    private fun shortCommit(value: String): String = if (value.length > 12) value.take(12) else value

    private fun formatNumber(value: Double, decimals: Int): String = String.format(PT_BR, "%,.${decimals}f", value)

    private fun formatPercentage(value: Double): String = String.format(PT_BR, "%.2f%%", value * 100)

    private fun JsonNode?.text(field: String, fallback: String): String =
        this?.get(field)?.takeUnless(JsonNode::isNull)?.asString()?.takeIf(String::isNotBlank) ?: fallback

    private fun JsonNode?.number(field: String, fallback: Double = 0.0): Double =
        this?.get(field)?.takeUnless(JsonNode::isNull)?.asDouble() ?: fallback

    private fun JsonNode?.boolean(field: String): Boolean =
        this?.get(field)?.takeUnless(JsonNode::isNull)?.asBoolean() ?: false

    private data class LoadTestReport(
        val executionStatus: String,
        val commit: String,
        val executedAtUtc: String,
        val environment: String,
        val resources: String,
        val baseUrl: String,
        val nominalTarget: Double,
        val nominalDuration: String,
        val warmUpTarget: Double,
        val warmUpDuration: String,
        val nominalRate: Double,
        val p99Milliseconds: Double,
        val technicalErrorRate: Double,
        val droppedIterations: Double,
        val completedEvaluations: Double,
        val thresholds: List<ThresholdResult>,
        val passed: Boolean,
    )

    private data class ThresholdResult(val metric: String, val criterion: String, val passed: Boolean)
    private data class MetricCard(val label: String, val value: String, val context: String, val passed: Boolean)
    private data class GraphMetric(val label: String, val ratio: Double, val value: String, val passed: Boolean)

    private companion object {
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        val LANDSCAPE_A4 = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
        val PAGE_WIDTH: Float = LANDSCAPE_A4.width
        val PAGE_HEIGHT: Float = LANDSCAPE_A4.height
        val CONTENT_WIDTH: Float = PAGE_WIDTH - PAGE_MARGIN * 2

        const val PAGE_MARGIN = 36f
        const val HEADER_HEIGHT = 80f
        const val METADATA_Y = 472f
        const val METRICS_Y = 398f
        const val SECTION_TITLE_Y = 380f
        const val CHART_Y = 214f
        const val CHART_WIDTH = 510f
        const val CHART_HEIGHT = 150f
        const val TABLE_TITLE_Y = 194f
        const val TABLE_TOP = 180f
        const val FOOTER_LINE_Y = 33f
        const val FOOTER_TEXT_Y = 19f
        const val MAX_THRESHOLD_ROWS = 6
        const val P99_LIMIT = 1_000.0
        const val ERROR_RATE_LIMIT = 0.01
        const val GRAPH_SCALE = 1.2f

        val HEADER_COLOR = Color(31, 41, 55)
        val ACCENT_COLOR = Color(13, 148, 136)
        val ACCENT_LIGHT = Color(153, 246, 228)
        val HEADER_SECONDARY = Color(209, 213, 219)
        val BODY_TEXT = Color(31, 41, 55)
        val MUTED_TEXT = Color(100, 116, 139)
        val PANEL_BACKGROUND = Color(248, 250, 252)
        val BORDER_COLOR = Color(226, 232, 240)
        val TABLE_HEADER_COLOR = Color(51, 65, 85)
        val TRACK_COLOR = Color(226, 232, 240)
        val TARGET_COLOR = Color(71, 85, 105)
        val PASSED_COLOR = Color(22, 163, 74)
        val FAILED_COLOR = Color(220, 38, 38)
        val PASSED_TEXT = Color(21, 128, 61)
        val FAILED_TEXT = Color(185, 28, 28)
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'")
    }
}

private class LoadTestPdfCanvas(private val pageWidth: Float) {
    /** Preenche uma área retangular com a cor indicada. */
    fun fillRect(stream: PDPageContentStream, x: Float, y: Float, width: Float, height: Float, color: Color) {
        stream.setNonStrokingColor(color)
        stream.addRect(x, y, width, height)
        stream.fill()
    }

    /** Contorna uma área retangular com a cor indicada. */
    fun strokeRect(stream: PDPageContentStream, x: Float, y: Float, width: Float, height: Float, color: Color) {
        stream.setStrokingColor(color)
        stream.setLineWidth(0.5f)
        stream.addRect(x, y, width, height)
        stream.stroke()
    }

    /** Desenha uma linha horizontal entre os pontos informados. */
    fun horizontalLine(stream: PDPageContentStream, fromX: Float, toX: Float, y: Float, color: Color) {
        stream.setStrokingColor(color)
        stream.setLineWidth(0.5f)
        stream.moveTo(fromX, y)
        stream.lineTo(toX, y)
        stream.stroke()
    }

    /** Desenha uma linha vertical entre os pontos informados. */
    fun verticalLine(stream: PDPageContentStream, x: Float, fromY: Float, toY: Float, color: Color, width: Float) {
        stream.setStrokingColor(color)
        stream.setLineWidth(width)
        stream.moveTo(x, fromY)
        stream.lineTo(x, toY)
        stream.stroke()
    }

    /** Escreve um texto, limitando sua largura quando necessário. */
    fun text(
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

    /** Escreve um texto alinhado pela margem direita. */
    fun rightText(
        stream: PDPageContentStream,
        text: String,
        right: Float,
        y: Float,
        font: PDFont,
        size: Float,
        color: Color,
    ) {
        val safe = safeText(text, font)
        text(stream, safe, right - textWidth(safe, font, size), y, font, size, color)
    }

    /** Escreve um texto centralizado no ponto horizontal informado. */
    fun centeredText(
        stream: PDPageContentStream,
        text: String,
        centerX: Float,
        y: Float,
        font: PDFont,
        size: Float,
        color: Color,
    ) {
        val safe = safeText(text, font)
        text(stream, safe, centerX - textWidth(safe, font, size) / 2f, y, font, size, color)
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

    private fun textWidth(text: String, font: PDFont, size: Float): Float = font.getStringWidth(text) / 1_000f * size
}
