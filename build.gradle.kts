plugins {
    base
    jacoco
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

group = "io.github.sequelcore"
version = "0.1.0-alpha.1"

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
val publishableArtifactIds = mapOf(
    ":java:core" to "mortar-core",
    ":java:dialect-postgres" to "mortar-dialect-postgres",
    ":java:runtime-jdbc" to "mortar-runtime-jdbc",
    ":java:spring-boot-starter" to "mortar-spring-boot-starter",
    ":java:processor" to "mortar-processor",
    ":java:testkit" to "mortar-testkit"
)
val publishableDescriptions = mapOf(
    ":java:core" to "Mortar framework-free query model and Java-first DSL contracts",
    ":java:dialect-postgres" to "Mortar PostgreSQL SQL renderer",
    ":java:runtime-jdbc" to "Mortar JDBC runtime adapter",
    ":java:spring-boot-starter" to "Mortar Spring Boot starter",
    ":java:processor" to "Mortar annotation processor for generated query metadata and executors",
    ":java:testkit" to "Mortar SQL and EXPLAIN assertion testkit"
)
val coverageExemptProjects = setOf(
    ":java:benchmarks"
)
val hasSigningConfiguration = providers.gradleProperty("signingInMemoryKey").isPresent
    || providers.gradleProperty("signing.secretKeyRingFile").isPresent
    || providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent

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

tasks.register("verifyPublishWorkflow") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validates that Maven Central release publishing is configured for public modules."

    doLast {
        val workflowFile = file(".github/workflows/publish.yml")
        if (!workflowFile.isFile) {
            throw GradleException("Publish workflow is missing: ${workflowFile.relativeTo(rootDir)}")
        }

        val workflowContent = workflowFile.readText()
        val requiredWorkflowFragments = listOf(
            "tags:",
            "v*",
            "DOPPLER_TOKEN",
            "publishToMavenCentral",
            "MAVEN_USERNAME",
            "MAVEN_PASSWORD",
            "GPG_PRIVATE_KEY",
            "GPG_PASSPHRASE",
            "softprops/action-gh-release"
        )
        val missingWorkflowFragments = requiredWorkflowFragments.filterNot(workflowContent::contains)
        if (missingWorkflowFragments.isNotEmpty()) {
            throw GradleException(
                "Publish workflow is missing required fragments: ${missingWorkflowFragments.joinToString(", ")}"
            )
        }

        val buildContent = file("build.gradle.kts").readText()
        val requiredBuildFragments = listOf(
            "io.github.sequelcore",
            "com.vanniktech.maven.publish",
            "publishToMavenCentral(automaticRelease = true)",
            "hasSigningConfiguration",
            "signAllPublications()",
            "mortar-core",
            "mortar-dialect-postgres",
            "mortar-runtime-jdbc",
            "mortar-spring-boot-starter",
            "mortar-processor",
            "mortar-testkit"
        )
        val missingBuildFragments = requiredBuildFragments.filterNot(buildContent::contains)
        if (missingBuildFragments.isNotEmpty()) {
            throw GradleException(
                "Publishing configuration is missing required fragments: ${missingBuildFragments.joinToString(", ")}"
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verifyBenchmarkWorkflow")
    dependsOn("verifyPublishWorkflow")
}

subprojects {
    val isCoverageExempt = project.path in coverageExemptProjects

    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    if (path in publishableJavaProjects) {
        apply(plugin = "com.vanniktech.maven.publish")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    if (path in publishableJavaProjects) {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)
            if (hasSigningConfiguration) {
                signAllPublications()
            }

            coordinates(
                rootProject.group.toString(),
                publishableArtifactIds.getValue(path),
                rootProject.version.toString()
            )

            pom {
                name.set("Mortar ${project.name}")
                description.set(publishableDescriptions.getValue(path))
                inceptionYear.set("2026")
                url.set("https://github.com/sequelcore/mortar")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("sequelcore")
                        name.set("Sequel")
                        url.set("https://github.com/sequelcore")
                    }
                }
                scm {
                    url.set("https://github.com/sequelcore/mortar")
                    connection.set("scm:git:git://github.com/sequelcore/mortar.git")
                    developerConnection.set("scm:git:ssh://git@github.com/sequelcore/mortar.git")
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
