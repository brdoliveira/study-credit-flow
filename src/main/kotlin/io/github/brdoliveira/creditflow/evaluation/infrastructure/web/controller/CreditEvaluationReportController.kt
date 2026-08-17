package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.controller

import io.github.brdoliveira.creditflow.evaluation.application.report.GenerateCreditEvaluationReportUseCase
import io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error.InvalidFilterException
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import java.time.*
import java.time.format.DateTimeFormatter

/** Expõe a geração do relatório PDF das avaliações. */
@RestController
@RequestMapping("/api/v1/credit-evaluations")
class CreditEvaluationReportController(private val generate: GenerateCreditEvaluationReportUseCase, private val clock: Clock = Clock.systemUTC()) {
    /** Gera o relatório mantendo o nome e os cabeçalhos HTTP existentes. */
    @GetMapping("/report.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun report(@RequestParam(required = false) decision: String?, @RequestParam(required = false) from: LocalDate?, @RequestParam(required = false) to: LocalDate?, @RequestParam params: Map<String, String>): ResponseEntity<ByteArray> {
        val filter = CreditEvaluationReportFilter(decision, from, to)
        try { filter.validate(params.keys) } catch (exception: IllegalArgumentException) { throw InvalidFilterException(exception.message ?: "Invalid report filter", exception) }
        val generatedAt = clock.instant()
        val bytes = generate.execute(
            filter.decision,
            filter.from?.atStartOfDay(ZoneOffset.UTC)?.toInstant(),
            filter.to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.minusNanos(1),
            generatedAt,
        )
        val date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(generatedAt.atOffset(ZoneOffset.UTC))
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("credit-evaluations-$date.pdf").build().toString()).body(bytes)
    }
}

private data class CreditEvaluationReportFilter(val decision: String?, val from: LocalDate?, val to: LocalDate?) {
    /** Valida os filtros privados usados para montar o relatório. */
    fun validate(parameterNames: Set<String>) {
        val allowed = setOf("decision", "from", "to")
        require(parameterNames.all { it in allowed }) { "Unknown filter: ${parameterNames - allowed}" }
        require(decision == null || decision in setOf("APPROVED", "REJECTED")) { "decision must be APPROVED or REJECTED" }
        require(from == null || to == null || !from.isAfter(to)) { "from must not be after to" }
    }
}
