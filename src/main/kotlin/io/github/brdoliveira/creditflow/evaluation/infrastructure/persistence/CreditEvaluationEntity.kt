package io.github.brdoliveira.creditflow.evaluation.infrastructure.persistence

import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Representa a linha persistida da avaliação, mantendo JSON como detalhe externo. */
@Entity
@Table(name = "credit_evaluation")
class CreditEvaluationEntity(
    @Id @Column(name = "evaluation_id", nullable = false, updatable = false) var evaluationId: UUID,
    @Column(name = "cpf_masked", nullable = false, length = 14, updatable = false) var maskedCpf: String,
    @Column(nullable = false, length = 16, updatable = false) var decision: String,
    @Column(name = "approved_amount", nullable = false, precision = 19, scale = 2, updatable = false) var approvedAmount: BigDecimal,
    @Column(name = "rule_version", nullable = false, length = 128, updatable = false) var ruleVersion: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "rule_results", nullable = false, columnDefinition = "jsonb", updatable = false) var ruleResults: String,
    @Column(name = "evaluated_at", nullable = false, updatable = false) var evaluatedAt: Instant,
    @Column(name = "duration_millis", nullable = false, updatable = false) var durationMillis: Long,
    @Column(name = "correlation_id", nullable = false, updatable = false) var correlationId: String,
) {
    /** Construtor exigido pelo provedor JPA. */
    constructor() : this(UUID(0, 0), "***.***.***-00", "REJECTED", BigDecimal.ZERO, "", "[]", Instant.EPOCH, 0, "")

    /** Converte a entidade para o modelo tipado, recebendo a desserialização do adaptador. */
    fun toDomain(results: List<RuleResult>) = CreditEvaluation(
        evaluationId = evaluationId,
        maskedCpf = maskedCpf,
        decision = CreditDecisionStatus.valueOf(decision),
        ruleResults = results,
        approvedAmount = approvedAmount,
        ruleSetVersion = ruleVersion,
        processedAt = evaluatedAt,
        processingTimeMs = durationMillis,
        correlationId = correlationId,
    )
}
