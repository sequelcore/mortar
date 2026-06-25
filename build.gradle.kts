plugins {
    base
    jacoco
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

group = "io.github.sequelcore"
version = "0.1.0-alpha.3"

val junitVersion = "6.1.0"
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
            "repeatCount",
            "default: \"2\"",
            "jmhR23PostR22JavaRuntime",
            "jmhR23PostR22JavaRuntimeAllocation",
            "jmhR23PostR22JavaRuntimeLatency",
            "jmhIncludes",
            "R23.2",
            "manifest.json",
            "commands.txt",
            "environment",
            "meminfo.txt",
            "review-notes.md",
            "mortar-r23-java-runtime-postgres-manifest-v1",
            "r23.2-post-r22-java-runtime",
            "evidenceFamily",
            "java-runtime-postgres",
            "scenarioFamilies",
            "scalar-read",
            "row-count-mutation",
            "returning-mutation",
            "batch-write",
            "worktreeState",
            "untrackedClean",
            "gitStatusFile",
            "limitations",
            "unsupportedRows",
            "actions/upload-artifact",
            "java/benchmarks/build/reports/jmh/r23.2-post-r22-java-runtime",
            "rust-tooling-lsp",
            "r23.3-rust-tooling-lsp",
            "mortar-r23-rust-tooling-criterion-manifest-v1",
            "r23_rust_tooling_lsp",
            "cargo bench -p sequel-mortar-lsp --bench r23_rust_tooling_lsp",
            "rust/target/criterion",
            "rust/target/r23.3-rust-tooling-lsp",
            "mortar-r23.3-rust-tooling-lsp-",
            "vscode-editor-latency",
            "r23.4-vscode-editor-latency",
            "mortar-r23-vscode-editor-latency-manifest-v1",
            "bun run test",
            "bun run test:screenshots",
            "editors/vscode/build/r23.4-vscode-editor-latency",
            "mortar-r23.4-vscode-editor-latency-",
            "retention-days: 90"
        )
        val missingFragments = requiredFragments.filterNot(content::contains)
        if (missingFragments.isNotEmpty()) {
            throw GradleException(
                "Benchmark workflow is missing required fragments: ${missingFragments.joinToString(", ")}"
            )
        }

        val forbiddenFragments = listOf("r20.3", "R20.3", "mortar-r20.3")
        val presentForbiddenFragments = forbiddenFragments.filter(content::contains)
        if (presentForbiddenFragments.isNotEmpty()) {
            throw GradleException(
                "Benchmark workflow still contains stale R20.3 fragments: "
                    + presentForbiddenFragments.joinToString(", ")
            )
        }
    }
}

tasks.register("verifyPublishWorkflow") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validates that release publication remains manual, guarded, and explicit."

    doLast {
        val workflowFile = file(".github/workflows/publish.yml")
        if (!workflowFile.isFile) {
            throw GradleException("Publish workflow is missing: ${workflowFile.relativeTo(rootDir)}")
        }

        val workflowContent = workflowFile.readText()
        val requiredWorkflowFragments = listOf(
            "workflow_dispatch:",
            "operation:",
            "release_ref:",
            "release_version:",
            "publish_java:",
            "publish_rust:",
            "publish_vscode:",
            "confirmation:",
            "permissions:",
            "contents: read",
            "if: " + "$" + "{{ inputs.operation == 'publish' && inputs.publish_java }}",
            "if: " + "$" + "{{ inputs.operation == 'publish' && inputs.publish_rust }}",
            "if: " + "$" + "{{ inputs.operation == 'publish' && inputs.publish_vscode }}",
            "environment: release",
            "Publishing requires release_ref=",
            "Publishing requires confirmation=",
            "publishToMavenLocal",
            "cargo package --list -p sequel-mortar-compiler",
            "cargo package --list -p sequel-mortar-cli",
            "cargo package --list -p sequel-mortar-lsp",
            "cargo publish --dry-run -p sequel-mortar-compiler",
            "bun run package:vsix",
            "dopplerhq/secrets-fetch-action@v2.0.0",
            "DOPPLER_TOKEN",
            "MAVEN_USERNAME",
            "MAVEN_PASSWORD",
            "GPG_PRIVATE_KEY",
            "GPG_PASSPHRASE",
            "publishAllPublicationsToMavenCentralRepository",
            "CARGO_REGISTRY_TOKEN",
            "cargo publish -p sequel-mortar-compiler",
            "cargo publish -p sequel-mortar-cli",
            "cargo publish -p sequel-mortar-lsp",
            "VSCE_PAT",
            "vsce publish --pre-release"
        )
        val missingWorkflowFragments = requiredWorkflowFragments.filterNot(workflowContent::contains)
        if (missingWorkflowFragments.isNotEmpty()) {
            throw GradleException(
                "Publish workflow is missing required fragments: ${missingWorkflowFragments.joinToString(", ")}"
            )
        }

        val forbiddenWorkflowFragments = listOf(
            "tags:",
            "pull_request:",
            "soft" + "props/action-gh" + "-release",
            "contents: " + "write",
            "secrets.MAVEN_USERNAME",
            "secrets.MAVEN_PASSWORD",
            "secrets.GPG_PRIVATE_KEY",
            "secrets.GPG_PASSPHRASE",
            "secrets.CARGO_REGISTRY_TOKEN",
            "secrets.VSCE_PAT"
        )
        val presentForbiddenWorkflowFragments = forbiddenWorkflowFragments.filter(workflowContent::contains)
        if (presentForbiddenWorkflowFragments.isNotEmpty()) {
            throw GradleException(
                "Publish workflow contains unguarded or direct-secret fragments: "
                    + presentForbiddenWorkflowFragments.joinToString(", ")
            )
        }

        val buildContent = file("build.gradle.kts").readText()
        val requiredBuildFragments = listOf(
            "io.github.sequelcore",
            "com.vanniktech.maven.publish",
            "hasSigningConfiguration",
            "signAllPublications()",
            "publishToMavenCentral(automaticRelease = true)",
            "coordinates(",
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

        val forbiddenBuildFragments = listOf(
            "publishAndReleaseToMaven" + "Central"
        )
        val presentForbiddenBuildFragments = forbiddenBuildFragments.filter(buildContent::contains)
        if (presentForbiddenBuildFragments.isNotEmpty()) {
            throw GradleException(
                "Publishing configuration contains unsupported remote publication fragments: "
                    + presentForbiddenBuildFragments.joinToString(", ")
            )
        }

        val nonPublicPublishableProjects = publishableJavaProjects.filter {
            it == ":java:benchmarks" || it.startsWith(":examples:") || it.startsWith(":editors:")
        }
        if (nonPublicPublishableProjects.isNotEmpty()) {
            throw GradleException(
                "Publishing configuration contains non-public modules: "
                    + nonPublicPublishableProjects.joinToString(", ")
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
            languageVersion.set(JavaLanguageVersion.of(25))
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

        tasks.matching { it.name == "generateMetadataFileForMavenPublication" }.configureEach {
            dependsOn("plainJavadocJar")
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
