package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error

import io.github.brdoliveira.creditflow.evaluation.application.port.IdempotencyKeyConflictException
import io.github.brdoliveira.creditflow.evaluation.application.port.InvalidIdempotencyKeyException
import io.github.brdoliveira.creditflow.evaluation.application.port.MissingIdempotencyKeyException
import io.github.brdoliveira.creditflow.infrastructure.observability.CorrelationIdFilter
import io.github.brdoliveira.creditflow.evaluation.infrastructure.observability.CreditMetrics
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** Centraliza a conversão de falhas da API em respostas HTTP estáveis. */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val metrics: CreditMetrics? = null,
) {
    /** Trata falhas de validação do corpo da requisição. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        val errors = exception.bindingResult.fieldErrors.map { error ->
            FieldError(error.field.removeSuffix("Valid").ifBlank { "request" }, error.defaultMessage ?: "invalid value")
        } + exception.bindingResult.globalErrors.map { error ->
            FieldError("cpf", error.defaultMessage ?: "invalid value")
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", request, errors)
    }

    /** Trata filtros, parâmetros e identificadores inválidos. */
    @ExceptionHandler(
        ConstraintViolationException::class,
        HandlerMethodValidationException::class,
        MethodArgumentTypeMismatchException::class,
        InvalidFilterException::class,
    )
    fun invalidFilter(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", exception.message ?: "Invalid request filter", request)

    /** Trata chaves de idempotência ausentes ou inválidas. */
    @ExceptionHandler(MissingIdempotencyKeyException::class, InvalidIdempotencyKeyException::class)
    fun invalidIdempotencyKey(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", exception.message ?: "Invalid idempotency key", request)

    /** Trata reutilização conflitante da chave de idempotência. */
    @ExceptionHandler(IdempotencyKeyConflictException::class)
    fun idempotencyConflict(
        exception: IdempotencyKeyConflictException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> = error(
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_KEY_CONFLICT",
        exception.message ?: "Idempotency key conflict",
        request,
    )

    /** Trata avaliações inexistentes. */
    @ExceptionHandler(EvaluationNotFoundException::class)
    fun notFound(request: HttpServletRequest): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "EVALUATION_NOT_FOUND", "Credit evaluation was not found", request)

    /** Trata indisponibilidade temporária de dependências. */
    @ExceptionHandler(DataAccessResourceFailureException::class)
    fun unavailable(request: HttpServletRequest): ResponseEntity<ApiError> {
        metrics?.recordTechnicalError("DEPENDENCY")
        return error(
            HttpStatus.SERVICE_UNAVAILABLE,
            "DEPENDENCY_UNAVAILABLE",
            "A required dependency is temporarily unavailable",
            request,
        )
    }

    /** Impede exposição de detalhes em falhas técnicas inesperadas. */
    @ExceptionHandler(Exception::class)
    fun unexpected(request: HttpServletRequest): ResponseEntity<ApiError> {
        metrics?.recordTechnicalError("INTERNAL")
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request)
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        fieldErrors: List<FieldError> = emptyList(),
    ): ResponseEntity<ApiError> = ResponseEntity.status(status).body(
        ApiError(status.value(), code, message, request.correlationId(), request.requestURI, fieldErrors),
    )
}

private fun HttpServletRequest.correlationId(): String =
    (getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE) as? String)
        ?: CorrelationIdFilter.normalizeCorrelationId(getHeader(CorrelationIdFilter.HEADER_NAME))
