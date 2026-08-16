package com.itau.credit.infrastructure.persistence

import com.itau.credit.application.port.CreditEvaluationSnapshot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "credit_evaluation")
class CreditEvaluationEntity(
    @Id
    @Column(name = "evaluation_id", nullable = false, updatable = false)
    var evaluationId: UUID,

    @Column(name = "cpf_masked", nullable = false, length = 14, updatable = false)
    var maskedCpf: String,

    @Column(nullable = false, length = 16, updatable = false)
    var decision: String,

    @Column(name = "approved_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    var approvedAmount: BigDecimal,

    @Column(name = "rule_version", nullable = false, length = 128, updatable = false)
    var ruleVersion: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_results", nullable = false, columnDefinition = "jsonb", updatable = false)
    var ruleResults: String,

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    var evaluatedAt: Instant,

    @Column(name = "duration_millis", nullable = false, updatable = false)
    var durationMillis: Long,

    @Column(name = "correlation_id", nullable = false, updatable = false)
    var correlationId: String,
) {
    constructor() : this(
        UUID(0, 0), "***.***.***-00", "", BigDecimal.ZERO, "", "[]", Instant.EPOCH, 0, "00000000-0000-0000-0000-000000000000",
    )

    fun toSnapshot() = CreditEvaluationSnapshot(
        evaluationId = evaluationId,
        maskedCpf = maskedCpf,
        decision = decision,
        approvedAmount = approvedAmount,
        ruleVersion = ruleVersion,
        ruleResults = ruleResults,
        evaluatedAt = evaluatedAt,
        durationMillis = durationMillis,
        correlationId = correlationId,
    )

    companion object {
        fun from(snapshot: CreditEvaluationSnapshot) = CreditEvaluationEntity(
            evaluationId = snapshot.evaluationId,
            maskedCpf = snapshot.maskedCpf,
            decision = snapshot.decision,
            approvedAmount = snapshot.approvedAmount,
            ruleVersion = snapshot.ruleVersion,
            ruleResults = snapshot.ruleResults,
            evaluatedAt = snapshot.evaluatedAt,
            durationMillis = snapshot.durationMillis,
            correlationId = snapshot.correlationId,
        )
    }
}
