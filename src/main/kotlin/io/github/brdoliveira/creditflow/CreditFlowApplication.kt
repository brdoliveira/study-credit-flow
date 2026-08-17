package io.github.brdoliveira.creditflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CreditFlowApplication

fun main(args: Array<String>) {
    runApplication<CreditFlowApplication>(*args)
}
