package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller

import io.github.brdoliveira.creditflow.evaluation.application.report.GenerateCreditEvaluationReportUseCase
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.dto.CreditEvaluationFilterCriteria
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Expõe a geração do relatório PDF das avaliações. */
@RestController
@RequestMapping("/api/v1/credit-evaluations")
class CreditEvaluationReportController(
    private val generate: GenerateCreditEvaluationReportUseCase,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Gera o relatório mantendo o nome e os cabeçalhos HTTP existentes. */
    @GetMapping("/report.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun report(
        @RequestParam(required = false) decision: String?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam params: Map<String, String>,
    ): ResponseEntity<ByteArray> {
        val filter = CreditEvaluationFilterCriteria(decision, from, to)
        filter.validate(params.keys)
        val generatedAt = clock.instant()
        val bytes = generate.execute(filter.toFilter(), generatedAt)
        val filename = "credit-evaluations-${FILE_DATE_FORMAT.format(generatedAt.atOffset(ZoneOffset.UTC))}.pdf"
        val disposition = ContentDisposition.attachment().filename(filename).build().toString()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .body(bytes)
    }
    private companion object {
        val FILE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
