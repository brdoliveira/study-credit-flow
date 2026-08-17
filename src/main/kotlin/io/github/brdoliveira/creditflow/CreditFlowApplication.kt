package io.github.brdoliveira.creditflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Inicializa o serviço de avaliação de crédito. */
@SpringBootApplication
class CreditFlowApplication

/** Executa a aplicação Spring Boot. */
fun main(args: Array<String>) {
    runApplication<CreditFlowApplication>(*args)
}
