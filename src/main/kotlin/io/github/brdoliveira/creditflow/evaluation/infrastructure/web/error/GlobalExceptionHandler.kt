package io.github.brdoliveira.creditflow.evaluation.infrastructure.web.error

import io.github.brdoliveira.creditflow.infrastructure.observability.CorrelationIdFilter
import io.github.brdoliveira.creditflow.infrastructure.web.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.*
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.*

/** Centraliza a conversão de falhas da API em respostas HTTP estáveis. */
@RestControllerAdvice
class GlobalExceptionHandler {
    /** Trata falhas de validação do corpo da requisição. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> = error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", request, exception.bindingResult.fieldErrors.map { FieldError(it.field, it.defaultMessage ?: "invalid value") })
    /** Trata filtros, parâmetros e identificadores inválidos. */
    @ExceptionHandler(ConstraintViolationException::class, HandlerMethodValidationException::class, MethodArgumentTypeMismatchException::class, InvalidFilterException::class)
    fun invalidFilter(exception: Exception, request: HttpServletRequest) = error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", exception.message ?: "Invalid request filter", request)
    /** Trata avaliações inexistentes. */
    @ExceptionHandler(EvaluationNotFoundException::class)
    fun notFound(request: HttpServletRequest) = error(HttpStatus.NOT_FOUND, "EVALUATION_NOT_FOUND", "Credit evaluation was not found", request)
    private fun error(status: HttpStatus, code: String, message: String, request: HttpServletRequest, fields: List<FieldError> = emptyList()) = ResponseEntity.status(status).body(ApiError(status.value(), code, message, correlation(request), request.requestURI, fields))
    private fun correlation(request: HttpServletRequest) = (request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE) as? String) ?: CorrelationIdFilter.normalizeCorrelationId(request.getHeader(CorrelationIdFilter.HEADER_NAME))
}
