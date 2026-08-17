package io.github.brdoliveira.creditflow.evaluation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName

class FeaturePackageOrganizationTest {
    @Test
    @DisplayName("AC-091 testes espelham os pacotes evaluation domain e application @spec:AC-091")
    // @spec:AC-091
    fun ac091TestPathsAndPackagesMirrorEvaluationProduction() {
        assertTestPackage("evaluation/domain", "$packageRoot.evaluation.domain")
        assertTestPackage("evaluation/application", "$packageRoot.evaluation.application")
        assertTrue(kotlinFiles(testRoot.resolve("domain")).isEmpty(), "testes de domínio não podem permanecer no namespace legado")
        assertTrue(kotlinFiles(testRoot.resolve("application")).isEmpty(), "testes de aplicação não podem permanecer no namespace legado")
    }

    @Test
    @DisplayName("AC-092 componentes específicos pertencem ao módulo evaluation @spec:AC-092")
    // @spec:AC-092
    fun ac092ComponentsBelongToEvaluationModule() {
        val components = mapOf(
            "evaluation/application/event/CreditEvaluationCompleted.kt" to "$packageRoot.evaluation.application.event",
            "evaluation/infrastructure/idempotency/CanonicalRequestHasher.kt" to "$packageRoot.evaluation.infrastructure.idempotency",
            "evaluation/infrastructure/messaging" to "$packageRoot.evaluation.infrastructure.messaging",
            "evaluation/infrastructure/observability/CreditMetrics.kt" to "$packageRoot.evaluation.infrastructure.observability",
            "evaluation/infrastructure/outbox" to "$packageRoot.evaluation.infrastructure.outbox",
        )

        components.forEach { (relativePath, expectedPackage) ->
            val component = sourceRoot.resolve(relativePath)
            assertTrue(Files.exists(component), "componente ausente de evaluation: $relativePath")
            kotlinFiles(component).forEach { file ->
                assertTrue(Files.readString(file).startsWith("package $expectedPackage"), "pacote incorreto em $file")
            }
        }
    }

    private fun assertTestPackage(relativePath: String, expectedPackage: String) {
        val directory = testRoot.resolve(relativePath)
        assertTrue(Files.isDirectory(directory), "diretório de teste ausente: $relativePath")
        val files = kotlinFiles(directory)
        assertTrue(files.isNotEmpty(), "diretório de teste vazio: $relativePath")
        files.forEach { file ->
            assertTrue(Files.readString(file).startsWith("package $expectedPackage"), "package declaration incorreta em $file")
        }
    }

    private fun kotlinFiles(path: Path): List<Path> = when {
        !Files.exists(path) -> emptyList()
        path.isRegularFile() -> listOf(path)
        else -> Files.walk(path).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.toList()
        }
    }

    private companion object {
        const val packageRoot: String = "io.github.brdoliveira" + ".creditflow"
        val sourceRoot: Path = Path.of("src/main/kotlin/io/github/brdoliveira/creditflow")
        val testRoot: Path = Path.of("src/test/kotlin/io/github/brdoliveira/creditflow")
    }
}
