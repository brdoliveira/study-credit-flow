package io.github.brdoliveira.creditflow.platform.health

/** Verifica se uma dependência obrigatória está disponível. */
fun interface RequiredDependencyProbe {
    /** Retorna se a dependência está pronta para uso. */
    fun isAvailable(): Boolean
}
