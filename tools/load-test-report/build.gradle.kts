plugins {
    kotlin("jvm") version "2.3.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("tools.jackson.core:jackson-databind:3.1.4")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val loadTestEvidence = providers.gradleProperty("loadTestEvidence")
    .orElse(".context/load-test-summary.json")
    .map(rootProject::file)
val loadTestPdf = providers.gradleProperty("loadTestPdf")
    .orElse(".context/load-test-report.pdf")
    .map(rootProject::file)

tasks.register<JavaExec>("generateLoadTestPdfReport") {
    group = "verification"
    description = "Gera o relatório PDF auditável a partir do resumo JSON do k6."
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.brdoliveira.creditflow.tools.performance.LoadTestPdfReportCli")
    inputs.file(loadTestEvidence)
    outputs.file(loadTestPdf)
    doFirst {
        args(loadTestEvidence.get().absolutePath, loadTestPdf.get().absolutePath)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
