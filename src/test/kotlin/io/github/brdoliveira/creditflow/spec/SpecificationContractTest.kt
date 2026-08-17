package io.github.brdoliveira.creditflow.spec

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecificationContractTest {
    private val projectRoot: Path = Path.of("").toAbsolutePath()

    @Test
    // @principle:P-002
    fun `P-002 source contains no hardcoded credentials`() {
        val credential = Regex("(?i)(api[_-]?key|password|senha)\\s*[:=]\\s*[\\\"'][^$\\{][^\\\"']{7,}[\\\"']")
        val violations = sourceFiles().filter { credential.containsMatchIn(it.readText()) }
        assertFalse(violations.any(), "Hardcoded credential found in: $violations")
    }

    @Test
    // @principle:P-003
    fun `P-003 domain is independent from frameworks`() {
        val forbidden = Regex("^import (org\\.springframework|jakarta\\.persistence|javax\\.persistence|org\\.hibernate)", RegexOption.MULTILINE)
        val domains = domainRoots()
        assertTrue(domains.isNotEmpty(), "At least one production domain package must exist")
        val violations = domains.asSequence().flatMap(::kotlinFiles).filter { forbidden.containsMatchIn(it.readText()) }
        assertFalse(violations.any(), "Framework import found in domain: $violations")
    }

    @Test
    // @principle:P-004
    fun `P-004 tests use PostgreSQL instead of H2`() {
        val build = projectRoot.resolve("build.gradle.kts").readText()
        assertFalse(build.contains("com.h2database", ignoreCase = true), "H2 must not be a project dependency")
    }

    @Test
    // @spec:AC-047
    fun `AC-047 documentation supports execution demonstration architecture and technical defense`() {
        val readme = projectRoot.resolve("README.md").readText()
        val requiredCommands = listOf("docker compose up --build", "gradlew.bat test", "gradlew.bat detekt", "node --test")
        val requiredDocuments = listOf(
            "docs/architecture.md",
            "docs/ai-usage.md",
            "docs/adrs/001-modular-monolith.md",
            "docs/adrs/002-postgresql.md",
            "docs/adrs/003-outbox-messaging.md",
            "docs/adrs/004-pdf-library.md",
            "docs/adrs/005-ecs-vs-eks.md",
            "docs/adrs/006-aurora-vs-dynamodb.md",
        )

        assertTrue(requiredCommands.all(readme::contains), "README must contain reproducible execution and test commands")
        assertTrue(requiredDocuments.all { Files.isRegularFile(projectRoot.resolve(it)) }, "Architecture, ADRs and AI usage must exist")
        assertTrue(readme.contains("Limitações") && readme.contains("Autenticação") && readme.contains("AWS"))
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

    private fun domainRoots(): List<Path> =
        Files.walk(projectRoot.resolve("src/main/kotlin")).use { paths ->
            paths.filter { Files.isDirectory(it) && it.fileName.toString() == "domain" }.toList()
        }
}
