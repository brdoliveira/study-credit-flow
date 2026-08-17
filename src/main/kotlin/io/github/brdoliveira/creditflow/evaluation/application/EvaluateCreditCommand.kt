package io.github.brdoliveira.creditflow.evaluation.application

import java.math.BigDecimal

/**
 * Dados de entrada do fluxo de avaliação, já validados pelo adaptador.
 *
 * @property customerName nome informado pelo cliente.
 * @property cpf CPF usado somente durante o processamento.
 * @property creditScore score considerado pelas regras.
 * @property currentInvoiceAmount valor atual da fatura.
 * @property totalLimit limite total contratado.
 * @property availableLimit limite disponível.
 * @property latePayments quantidade de atrasos.
 * @property monthlySpending gastos dos três meses mais recentes.
 * @property correlationId identificador de correlação da requisição.
 */
data class EvaluateCreditCommand(
    val customerName: String,
    val cpf: String,
    val creditScore: Int,
    val currentInvoiceAmount: BigDecimal,
    val totalLimit: BigDecimal,
    val availableLimit: BigDecimal,
    val latePayments: Int,
    val monthlySpending: List<BigDecimal>,
    val correlationId: String,
) {
    /** Produz uma representação estável usada na detecção de replays. */
    fun idempotencySource(): String = listOf(
        customerName,
        cpf.filter(Char::isDigit),
        creditScore,
        currentInvoiceAmount.toPlainString(),
        totalLimit.toPlainString(),
        availableLimit.toPlainString(),
        latePayments,
        monthlySpending.joinToString(",") { it.toPlainString() },
    ).joinToString("|")
}
