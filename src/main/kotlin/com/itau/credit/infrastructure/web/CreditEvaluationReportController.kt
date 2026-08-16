package com.itau.credit.infrastructure.web

import com.itau.credit.application.report.CreditEvaluationReport
import com.itau.credit.application.report.CreditEvaluationReportDataSource
import com.itau.credit.application.report.CreditEvaluationReportFilter
import com.itau.credit.application.report.CreditEvaluationReportGenerator
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/v1/credit-evaluations")
class CreditEvaluationReportController(
    private val service: CreditEvaluationReportService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @GetMapping("/report.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun report(
        @RequestParam(required = false) decision: String?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam params: Map<String, String>,
        @RequestHeader(value = "X-Correlation-ID", required = false) correlationId: String?,
    ): ResponseEntity<ByteArray> {
        val filter = CreditEvaluationReportFilter(decision, from, to)
        try {
            filter.validate(params.keys)
        } catch (exception: IllegalArgumentException) {
            throw InvalidFilterException(exception.message ?: "Invalid report filter")
        }
        val generatedAt = clock.instant()
        val bytes = service.generate(filter, generatedAt, correlationId.orEmpty())
        val fileDate = FILE_DATE_FORMAT.format(generatedAt.atOffset(ZoneOffset.UTC))
        val disposition = ContentDisposition.attachment().filename("credit-evaluations-$fileDate.pdf").build()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(bytes)
    }

    companion object {
        private val FILE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

fun interface CreditEvaluationReportService {
    fun generate(filter: CreditEvaluationReportFilter, generatedAt: java.time.Instant, correlationId: String): ByteArray
}

class DefaultCreditEvaluationReportService(
    private val dataSource: CreditEvaluationReportDataSource,
    private val generator: CreditEvaluationReportGenerator,
) : CreditEvaluationReportService {
    override fun generate(
        filter: CreditEvaluationReportFilter,
        generatedAt: java.time.Instant,
        correlationId: String,
    ): ByteArray = generator.generate(CreditEvaluationReport(filter, generatedAt, dataSource.findAll(filter)))
}
