package com.itau.credit.application.report

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

fun interface CreditEvaluationReportGenerator {
    fun generate(report: CreditEvaluationReport): ByteArray
}

data class CreditEvaluationReport(
    val filter: CreditEvaluationReportFilter,
    val generatedAt: Instant,
    val evaluations: List<CreditEvaluationReportRow>,
)

data class CreditEvaluationReportRow(
    val evaluationId: UUID,
    val maskedCpf: String,
    val decision: String,
    val approvedAmount: BigDecimal,
    val processedAt: Instant,
)

fun interface CreditEvaluationReportDataSource {
    fun findAll(filter: CreditEvaluationReportFilter): List<CreditEvaluationReportRow>
}
