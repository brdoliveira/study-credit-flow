package io.github.brdoliveira.creditflow.evaluation.application.report

/** Indica que a quantidade de avaliações excede o limite seguro do relatório síncrono. */
class ReportRowLimitExceededException(
    val totalRows: Long,
    val maximumRows: Int,
) : IllegalStateException("Report contains $totalRows rows; narrow the filters to at most $maximumRows rows")
