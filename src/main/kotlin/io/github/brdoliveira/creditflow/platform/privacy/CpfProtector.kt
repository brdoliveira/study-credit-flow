package io.github.brdoliveira.creditflow.platform.privacy

import org.springframework.stereotype.Component

/** Impede que o CPF completo alcance persistência, logs ou modelos externos. */
@Component
class CpfProtector {
    /** Mascara o CPF, preservando somente os dois últimos dígitos. */
    fun mask(cpf: String): String {
        val digits = cpf.filter(Char::isDigit)
        require(digits.length == CPF_LENGTH) { "CPF must contain 11 digits" }
        return "***.***.***-${digits.takeLast(2)}"
    }

    private companion object {
        const val CPF_LENGTH = 11
    }
}
