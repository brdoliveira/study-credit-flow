package io.github.brdoliveira.creditflow.tools.performance

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadTestPdfReportGeneratorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `report presents indicators chart thresholds and audit metadata`() {
        val evidence = tempDir.resolve("load-test-summary.json")
        Files.writeString(evidence, EVIDENCE)

        val bytes = LoadTestPdfReportGenerator().generate(evidence)

        assertTrue(bytes.take(4).toByteArray().contentEquals("%PDF".toByteArray()))
        Loader.loadPDF(bytes).use { document ->
            val text = PDFTextStripper().getText(document)
            assertEquals(1, document.numberOfPages)
            assertTrue(text.contains("Relatório de teste de carga"))
            assertTrue(text.contains("Observado x limite"))
            assertTrue(text.contains("10.000,2 / min"))
            assertTrue(text.contains("334,8 ms"))
            assertTrue(text.contains("Critérios de aprovação"))
            assertTrue(text.contains("5 de 5 critérios atendidos"))
            assertTrue(text.contains("Commit f9aa3b37569f"))
            assertTrue(text.contains("Página 1 de 1"))
        }
    }

    private companion object {
        val EVIDENCE = """
            {
              "executionStatus": "completed",
              "commit": "f9aa3b37569faa0f0de0dc6a1aee14c4e6e7c37d",
              "executedAtUtc": "2026-08-16T23:15:20Z",
              "environment": "isolated-local",
              "resources": "docker=12 CPU/7.7 GB; app=400 threads; hikari=40",
              "configuration": {
                "baseUrl": "http://localhost:18080",
                "nominalRatePerMinute": 10000,
                "nominalDuration": "5m",
                "warmUpRatePerMinute": 1000,
                "warmUpDuration": "1m"
              },
              "observed": {
                "nominalRatePerMinute": 10000.2,
                "p99Milliseconds": 334.8,
                "technicalErrorRate": 0,
                "droppedIterations": 0,
                "completedEvaluations": 51002
              },
              "thresholds": [
                { "metric": "checks{scenario:nominal}", "threshold": "rate==1", "passed": true },
                { "metric": "dropped_iterations", "threshold": "count==0", "passed": true },
                { "metric": "technical_error_rate{scenario:nominal}", "threshold": "rate<0.01", "passed": true },
                { "metric": "iterations{scenario:nominal}", "threshold": "count>=50000", "passed": true },
                { "metric": "http_req_duration{scenario:nominal}", "threshold": "p(99)<1000", "passed": true }
              ],
              "passed": true
            }
        """.trimIndent()
    }
}
