plugins {
    base
    jacoco
}

group = "dev.mortar"
version = "0.1.0-SNAPSHOT"

val junitVersion = "6.0.3"
val assertjVersion = "3.27.7"
val publishableJavaProjects = setOf(
    ":java:core",
    ":java:dialect-postgres",
    ":java:runtime-jdbc",
    ":java:spring-boot-starter",
    ":java:processor",
    ":java:testkit"
)
val coverageExemptProjects = setOf(
    ":java:benchmarks"
)

tasks.register("verifyBenchmarkWorkflow") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validates that the retained benchmark workflow is present and publishes JMH artifacts."

    doLast {
        val workflowFile = file(".github/workflows/benchmarks.yml")
        if (!workflowFile.isFile) {
            throw GradleException("Benchmark workflow is missing: ${workflowFile.relativeTo(rootDir)}")
        }

        val content = workflowFile.readText()
        val requiredFragments = listOf(
            "workflow_dispatch:",
            "jmhPostgresExecution",
            "jmhPostgresExecutionAllocation",
            "jmhPostgresExecutionLatency",
            "jmhIncludes",
            "actions/upload-artifact",
            "java/benchmarks/build/reports/jmh"
        )
        val missingFragments = requiredFragments.filterNot(content::contains)
        if (missingFragments.isNotEmpty()) {
            throw GradleException(
                "Benchmark workflow is missing required fragments: ${missingFragments.joinToString(", ")}"
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verifyBenchmarkWorkflow")
}

subprojects {
    val isCoverageExempt = project.path in coverageExemptProjects

    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    if (path in publishableJavaProjects) {
        apply(plugin = "maven-publish")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    if (path in publishableJavaProjects) {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])

                    pom {
                        name.set("Mortar ${project.name}")
                        description.set(project.description ?: "Mortar Java module")
                        url.set("https://github.com/sequelcore/mortar")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                        developers {
                            developer {
                                id.set("sequelcore")
                                name.set("Ricardo Armenta")
                                organization.set("Sequel")
                            }
                        }
                        scm {
                            connection.set("scm:git:https://github.com/sequelcore/mortar.git")
                            developerConnection.set("scm:git:ssh://git@github.com/sequelcore/mortar.git")
                            url.set("https://github.com/sequelcore/mortar")
                        }
                    }
                }
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:all",
                "-Werror",
                "-parameters"
            )
        )
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testImplementation"("org.assertj:assertj-core:$assertjVersion")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.register("checkNoWildcardImports") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Fails when Java sources contain wildcard imports."

        doLast {
            val sourceFiles = fileTree(projectDir) {
                include("src/**/*.java")
            }

            val violations = sourceFiles.files
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (Regex("""^\s*import\s+[^;]*\.\*;""").containsMatchIn(line)) {
                            "${file.relativeTo(rootDir)}:${index + 1}"
                        } else {
                            null
                        }
                    }
                }

            if (violations.isNotEmpty()) {
                throw GradleException("Wildcard imports are not allowed:\n${violations.joinToString("\n")}")
            }
        }
    }

    tasks.named("check") {
        dependsOn("checkNoWildcardImports")
        if (!isCoverageExempt) {
            dependsOn("jacocoTestCoverageVerification")
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        enabled = !isCoverageExempt
        classDirectories.setFrom(
            files(
                classDirectories.files.map { classesDir ->
                    fileTree(classesDir) {
                        exclude("**/Q*.class")
                        exclude("**/Q*$*.class")
                    }
                }
            )
        )
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }
}
