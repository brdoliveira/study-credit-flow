package com.itau.credit.infrastructure.web

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
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val errors = exception.bindingResult.fieldErrors.map { error ->
            ApiFieldError(error.field.removeSuffix("Valid").ifBlank { "request" }, error.defaultMessage ?: "invalid value")
        } + exception.bindingResult.globalErrors.map { error ->
            ApiFieldError("cpf", error.defaultMessage ?: "invalid value")
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", request, errors)
    }

    @ExceptionHandler(ConstraintViolationException::class, HandlerMethodValidationException::class, MethodArgumentTypeMismatchException::class, InvalidFilterException::class)
    fun invalidFilter(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", exception.message ?: "Invalid request filter", request)

    @ExceptionHandler(EvaluationNotFoundException::class)
    fun notFound(exception: EvaluationNotFoundException, request: HttpServletRequest): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "EVALUATION_NOT_FOUND", "Credit evaluation was not found", request)

    @ExceptionHandler(DataAccessResourceFailureException::class)
    fun unavailable(exception: DataAccessResourceFailureException, request: HttpServletRequest): ResponseEntity<ApiError> =
        error(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "A required dependency is temporarily unavailable", request)

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request)

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        fieldErrors: List<ApiFieldError> = emptyList()
    ): ResponseEntity<ApiError> = ResponseEntity.status(status).body(
        ApiError(status.value(), code, message, request.getHeader("X-Correlation-ID").validCorrelationId(), request.requestURI, fieldErrors)
    )
}

private fun String?.validCorrelationId(): String = this?.takeIf { it.isNotBlank() && it.length <= 128 } ?: UUID.randomUUID().toString()
