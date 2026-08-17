package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error

import java.util.UUID

/** Indica que a avaliação solicitada não existe. */
class EvaluationNotFoundException(evaluationId: UUID) :
    RuntimeException("Credit evaluation $evaluationId was not found")
