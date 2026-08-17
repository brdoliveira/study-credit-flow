package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecisionStatus
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.calculation.CreditLimitCalculator
import io.github.brdoliveira.creditflow.evaluation.domain.rule.RuleEngine
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Executa o domínio, calcula o valor elegível e persiste a avaliação. */
class EvaluateRevolvingCreditUseCase(
    private val ruleEngine: RuleEngine,
    private val creditLimitCalculator: CreditLimitCalculator,
    private val repository: CreditEvaluationRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> UUID = UUID::randomUUID,
    private val ruleSetVersion: String = "2026.08",
) {
    /** Avalia o comando e devolve o registro persistido. */
    fun execute(command: EvaluateCreditCommand): CreditEvaluation {
        val startedAt = clock.instant()
        val decision = ruleEngine.evaluate(CreditEvaluationContext(
            command.creditScore,
            command.currentInvoiceAmount, command.totalLimit, command.availableLimit,
            command.latePayments, command.monthlySpending,
        ))
        val approved = if (decision.status == CreditDecisionStatus.APPROVED) {
            creditLimitCalculator.calculate(command.availableLimit, command.creditScore)
        } else {
            BigDecimal.ZERO
        }
        val completedAt = clock.instant()
        return repository.save(
            CreditEvaluation(
                evaluationId = idGenerator(),
                maskedCpf = command.cpf.maskedCpf(),
                decision = decision.status,
                ruleResults = decision.ruleResults,
                approvedAmount = approved,
                ruleSetVersion = ruleSetVersion,
                processedAt = completedAt,
                processingTimeMs = Duration.between(startedAt, completedAt).toMillis(),
                correlationId = command.correlationId,
            ),
        )
    }

    private fun String.maskedCpf(): String {
        val digits = filter(Char::isDigit)
        require(digits.length == 11) { "CPF must contain 11 digits" }
        return "***.***.***-${digits.takeLast(2)}"
    }
}
