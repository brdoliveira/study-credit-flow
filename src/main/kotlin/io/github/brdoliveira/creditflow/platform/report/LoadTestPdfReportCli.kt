package io.github.brdoliveira.creditflow.platform.report

import java.nio.file.Files
import java.nio.file.Path

/** Expõe a geração do relatório de carga para execução pela tarefa Gradle. */
object LoadTestPdfReportCli {
    /** Gera um PDF de desempenho a partir dos caminhos de entrada e saída informados. */
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Uso: LoadTestPdfReportCli <evidencia.json> <relatorio.pdf>" }
        val evidence = Path.of(args[0])
        val output = Path.of(args[1])
        output.parent?.let(Files::createDirectories)
        Files.write(output, LoadTestPdfReportGenerator().generate(evidence))
        println("Relatório PDF gerado em ${output.toAbsolutePath()}")
    }
}
