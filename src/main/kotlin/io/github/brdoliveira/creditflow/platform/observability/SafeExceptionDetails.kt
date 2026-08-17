package io.github.brdoliveira.creditflow.platform.observability

/** Representa somente metadados de exceção apropriados para logs operacionais. */
data class SafeExceptionDetails(
    val type: String,
    val frames: String,
) {
    companion object {
        private const val MAX_FRAMES = 10

        /** Remove mensagem, causa, argumentos e demais valores potencialmente sensíveis. */
        fun from(error: Throwable): SafeExceptionDetails = SafeExceptionDetails(
            type = error.javaClass.simpleName.ifBlank { error.javaClass.name },
            frames = error.stackTrace
                .asSequence()
                .take(MAX_FRAMES)
                .joinToString("|") { frame ->
                    "${frame.className}.${frame.methodName}:${frame.lineNumber.coerceAtLeast(0)}"
                },
        )
    }
}
