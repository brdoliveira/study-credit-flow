package io.github.brdoliveira.creditflow.platform.observability

import org.slf4j.MDC

/** Delimita o contexto de correlação de uma operação executada fora da thread HTTP. */
object CorrelationLogContext {
    /** Executa [action] com a correlação informada e restaura o MDC anterior ao terminar. */
    fun <T> withCorrelationId(correlationId: String, action: () -> T): T {
        val previousContext = MDC.getCopyOfContextMap()
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId)
        return try {
            action()
        } finally {
            if (previousContext == null) {
                MDC.clear()
            } else {
                MDC.setContextMap(previousContext)
            }
        }
    }
}
