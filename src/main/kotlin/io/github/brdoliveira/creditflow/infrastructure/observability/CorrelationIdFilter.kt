package io.github.brdoliveira.creditflow.infrastructure.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val correlationId = normalizeCorrelationId(request.getHeader(HEADER_NAME))
        val wrappedRequest = CorrelatedRequest(request, correlationId)
        wrappedRequest.setAttribute(REQUEST_ATTRIBUTE, correlationId)
        response.setHeader(HEADER_NAME, correlationId)
        MDC.put(MDC_KEY, correlationId)
        try {
            chain.doFilter(wrappedRequest, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private class CorrelatedRequest(
        request: HttpServletRequest,
        private val correlationId: String,
    ) : HttpServletRequestWrapper(request) {
        override fun getHeader(name: String): String? =
            if (name.equals(HEADER_NAME, ignoreCase = true)) correlationId else super.getHeader(name)
    }

    companion object {
        const val HEADER_NAME = "X-Correlation-ID"
        const val REQUEST_ATTRIBUTE = "creditFlow.correlationId"
        const val MDC_KEY = "correlationId"
        private val VALID_ID = Regex("[A-Za-z0-9._-]{1,128}")

        fun normalizeCorrelationId(value: String?): String =
            value?.takeIf(VALID_ID::matches) ?: UUID.randomUUID().toString()
    }
}
