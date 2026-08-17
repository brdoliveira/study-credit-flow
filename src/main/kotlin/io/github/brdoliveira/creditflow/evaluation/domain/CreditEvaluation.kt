package io.github.brdoliveira.creditflow.evaluation.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Registro imutável produzido pelo caso de uso e persistido pela porta.
 *
 * @property evaluationId identificador público da avaliação.
 * @property maskedCpf CPF protegido para exposição e auditoria.
 * @property decision resultado consolidado pelo domínio.
 * @property approvedAmount valor aprovado ou zero em caso de reprovação.
 * @property ruleSetVersion versão das regras executadas.
 * @property processedAt instante de conclusão.
 * @property processingTimeMs duração total em milissegundos.
 * @property correlationId identificador de correlação da requisição.
 */
data class CreditEvaluation(
    val evaluationId: UUID,
    val maskedCpf: String,
    val decision: CreditDecisionStatus,
    val ruleResults: List<RuleResult>,
    val approvedAmount: BigDecimal,
    val ruleSetVersion: String,
    val processedAt: Instant,
    val processingTimeMs: Long,
    val correlationId: String,
)
