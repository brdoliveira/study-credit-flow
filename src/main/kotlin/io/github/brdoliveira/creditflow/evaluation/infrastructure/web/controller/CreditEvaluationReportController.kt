package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller

import io.github.brdoliveira.creditflow.application.report.*
import io.github.brdoliveira.creditflow.infrastructure.web.InvalidFilterException
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import java.time.*
import java.time.format.DateTimeFormatter

/** Expõe a geração do relatório PDF das avaliações. */
@RestController
@RequestMapping("/api/v1/credit-evaluations")
class CreditEvaluationReportController(private val dataSource: CreditEvaluationReportDataSource, private val generator: CreditEvaluationReportGenerator, private val clock: Clock = Clock.systemUTC()) {
    /** Gera o relatório mantendo o nome e os cabeçalhos HTTP existentes. */
    @GetMapping("/report.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun report(@RequestParam(required = false) decision: String?, @RequestParam(required = false) from: LocalDate?, @RequestParam(required = false) to: LocalDate?, @RequestParam params: Map<String, String>): ResponseEntity<ByteArray> {
        val filter = CreditEvaluationReportFilter(decision, from, to)
        try { filter.validate(params.keys) } catch (exception: IllegalArgumentException) { throw InvalidFilterException(exception.message ?: "Invalid report filter", exception) }
        val generatedAt = clock.instant()
        val bytes = generator.generate(CreditEvaluationReport(filter, generatedAt, dataSource.findAll(filter)))
        val date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(generatedAt.atOffset(ZoneOffset.UTC))
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("credit-evaluations-$date.pdf").build().toString()).body(bytes)
    }
}
