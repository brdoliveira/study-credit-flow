package com.itau.credit.spec

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse

class SpecificationContractTest {
    private val projectRoot: Path = Path.of("").toAbsolutePath()

    @Test
    fun `@principle:P-002 source contains no hardcoded credentials`() {
        val credential = Regex("(?i)(api[_-]?key|password|senha)\\s*[:=]\\s*[\\\"'][^$\\{][^\\\"']{7,}[\\\"']")
        val violations = sourceFiles().filter { credential.containsMatchIn(it.readText()) }
        assertFalse(violations.any(), "Hardcoded credential found in: $violations")
    }

    @Test
    fun `@principle:P-003 domain is independent from frameworks`() {
        val forbidden = Regex("^import (org\\.springframework|jakarta\\.persistence)", RegexOption.MULTILINE)
        val domain = projectRoot.resolve("src/main/kotlin/com/itau/credit/domain")
        val violations = kotlinFiles(domain).filter { forbidden.containsMatchIn(it.readText()) }
        assertFalse(violations.any(), "Framework import found in domain: $violations")
    }

    @Test
    fun `@principle:P-004 tests use PostgreSQL instead of H2`() {
        val build = projectRoot.resolve("build.gradle.kts").readText()
        assertFalse(build.contains("com.h2database", ignoreCase = true), "H2 must not be a project dependency")
    }

    private fun sourceFiles(): Sequence<Path> =
        Files.walk(projectRoot.resolve("src")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension in setOf("kt", "yml", "yaml") }.toList().asSequence()
        }

    private fun kotlinFiles(root: Path): Sequence<Path> {
        if (!Files.exists(root)) return emptySequence()
        return Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "kt" }.toList().asSequence()
        }
    }
}
