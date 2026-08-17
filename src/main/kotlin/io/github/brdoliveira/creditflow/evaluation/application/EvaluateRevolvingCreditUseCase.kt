package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.domain.calculation.CreditLimitCalculator
import io.github.brdoliveira.creditflow.domain.model.CreditEvaluationContext
import io.github.brdoliveira.creditflow.domain.rule.RuleEngine
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.domain.CreditDecision
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Executa regras, calcula o limite aprovado e persiste uma avaliação tipada. */
class EvaluateRevolvingCreditUseCase(
    private val ruleEngine: RuleEngine,
    private val creditLimitCalculator: CreditLimitCalculator,
    private val repository: CreditEvaluationRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    /** Avalia o comando e devolve o registro persistido. */
    fun execute(command: EvaluateCreditCommand): CreditEvaluation {
        val startedAt = clock.instant()
        val decision = ruleEngine.evaluate(CreditEvaluationContext(
            command.customerName, command.cpf, command.creditScore,
            command.currentInvoiceAmount, command.totalLimit, command.availableLimit,
            command.latePayments, command.monthlySpending,
        ))
        val typedDecision = CreditDecision(
            if (decision.status == io.github.brdoliveira.creditflow.domain.model.CreditDecisionStatus.APPROVED) CreditDecision.Status.APPROVED else CreditDecision.Status.REJECTED,
            decision.ruleResults.map { RuleResult(it.code, it.name, RuleResult.Severity.valueOf(it.severity.name), RuleResult.Status.valueOf(it.status.name), it.reason, it.facts) },
        )
        val approved = if (typedDecision.status == CreditDecision.Status.APPROVED) creditLimitCalculator.calculate(command.availableLimit, command.creditScore, true) else BigDecimal.ZERO
        val completedAt = clock.instant()
        return repository.save(CreditEvaluation(idGenerator(), command.cpf.maskedCpf(), typedDecision, approved, "current", completedAt, Duration.between(startedAt, completedAt).toMillis(), command.correlationId))
    }

    private fun String.maskedCpf(): String {
        val digits = filter(Char::isDigit)
        require(digits.length == 11) { "CPF must contain 11 digits" }
        return "***.***.***-${digits.takeLast(2)}"
    }
}
