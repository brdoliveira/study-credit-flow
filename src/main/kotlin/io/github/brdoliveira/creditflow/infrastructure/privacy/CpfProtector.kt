package io.github.brdoliveira.creditflow.infrastructure.privacy

import org.springframework.stereotype.Component

/** Keeps the CPF out of persistence, logs and outward-facing models. */
@Component
class CpfProtector {
    fun mask(cpf: String): String {
        val digits = cpf.filter(Char::isDigit)
        require(digits.length == CPF_LENGTH) { "CPF must contain 11 digits" }
        return "***.***.***-${digits.takeLast(2)}"
    }

    private companion object {
        const val CPF_LENGTH = 11
    }
}
