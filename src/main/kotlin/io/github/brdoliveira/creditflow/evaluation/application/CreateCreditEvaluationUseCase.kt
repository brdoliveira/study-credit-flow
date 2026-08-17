package io.github.brdoliveira.creditflow.evaluation.application

import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationMetrics
import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyRepository
import java.time.Duration

/** Coordena idempotência, avaliação e métricas da jornada de criação. */
class CreateCreditEvaluationUseCase(
    private val evaluator: EvaluateRevolvingCreditUseCase,
    private val idempotencyRepository: IdempotencyRepository,
    private val metrics: CreditEvaluationMetrics,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** Cria ou recupera uma avaliação para a chave idempotente informada. */
    fun execute(command: EvaluateCreditCommand, idempotencyKey: String?): IdempotentCreditEvaluationResult {
        val startedAt = nanoTime()
        val execution = idempotencyRepository.execute(
            idempotencyKey,
            command.idempotencySource(),
        ) {
            CreateCreditEvaluationResult(evaluator.execute(command), command.customerName)
        }
        if (!execution.replayed) {
            metrics.record(execution.result.evaluation, Duration.ofNanos(nanoTime() - startedAt))
        }
        return IdempotentCreditEvaluationResult(execution.result, execution.replayed)
    }
}
