description = "JMH benchmarks for Mortar performance measurement."

val r20GeneratedFixedReadIncludes =
    "PostgresExecutionBenchmark\\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$"
val r20DslShapesIncludes =
    "PostgresExecutionBenchmark\\.(plainJdbcFetch|mortarJdbcFetch|mortarPreRenderedJdbcFetch|plainJdbcJoinPageFetch|mortarJoinPageFetch|plainJdbcUpdateBatch|mortarUpdateBatch)$"
val r23PostR22JavaRuntimeIncludes =
    "PostgresExecutionBenchmark\\.(plainJdbcCountActive|mortarCountActive|plainJdbcExistsActive|mortarExistsActive|plainJdbcInsertRowCount|mortarInsertRowCount|plainJdbcUpdateRowCount|mortarUpdateRowCount|plainJdbcDeleteRowCount|mortarDeleteRowCount|plainJdbcInsertReturningFetch|mortarInsertReturningFetch|plainJdbcInsertReturningFetchOptional|mortarInsertReturningFetchOptional|plainJdbcUpdateBatch|mortarUpdateBatch)$"

dependencies {
    implementation(project(":java:core"))
    implementation(project(":java:dialect-postgres"))
    implementation(project(":java:runtime-jdbc"))
    implementation("com.querydsl:querydsl-sql:5.1.0")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.5")
    implementation("org.jetbrains:annotations:26.1.0")
    implementation("org.jooq:jooq:3.20.9")
    implementation("org.openjdk.jmh:jmh-core:1.37")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    compileOnly(project(":java:processor"))
    annotationProcessor(project(":java:processor"))
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}

fun JavaExec.configureJmhMain(defaultIncludes: String? = null) {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")

    val includes = providers.gradleProperty("jmhIncludes")
    if (includes.isPresent) {
        args(includes.get())
    } else if (defaultIncludes != null) {
        args(defaultIncludes)
    }
}

fun JavaExec.addConfiguredJmhExtraArgs() {
    val extraArgs = providers.gradleProperty("jmhArgs")
    if (extraArgs.isPresent) {
        args(extraArgs.get().split(" ").filter(String::isNotBlank))
    }
}

fun JavaExec.addDefaultJmhProfile(vararg profileArgs: String) {
    if (!providers.gradleProperty("jmhArgs").isPresent) {
        args(*profileArgs)
    }
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs Mortar JMH benchmarks. Use -PjmhIncludes=<regex> to filter benchmarks."
    configureJmhMain()
    addConfiguredJmhExtraArgs()
}

tasks.register<JavaExec>("jmhAllocation") {
    group = "verification"
    description = "Runs Mortar JMH benchmarks with the JMH GC profiler for allocation data."
    configureJmhMain()
    args("-prof", "gc")
    addConfiguredJmhExtraArgs()
}

tasks.register<JavaExec>("jmhBaseline") {
    group = "verification"
    description = "Runs the reproducible Mortar baseline JMH profile and writes JSON results under build/reports/jmh."
    configureJmhMain()
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/baseline.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhBaselineAllocation") {
    group = "verification"
    description = "Runs the reproducible Mortar baseline JMH profile with GC allocation metrics."
    configureJmhMain()
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-prof", "gc",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/baseline-allocation.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhPostgresExecution") {
    group = "verification"
    description = "Runs real PostgreSQL execution benchmarks through Testcontainers."
    configureJmhMain("PostgresExecutionBenchmark")
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/postgres-execution.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhPostgresExecutionLatency") {
    group = "verification"
    description = "Runs real PostgreSQL execution benchmarks in JMH sample-time mode for latency percentiles."
    configureJmhMain("PostgresExecutionBenchmark")
    addDefaultJmhProfile(
        "-bm", "sample",
        "-tu", "ms",
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/postgres-execution-latency.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhPostgresExecutionAllocation") {
    group = "verification"
    description = "Runs real PostgreSQL execution benchmarks with the JMH GC profiler."
    configureJmhMain("PostgresExecutionBenchmark")
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-prof", "gc",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/postgres-execution-allocation.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR20GeneratedFixedRead") {
    group = "verification"
    description = "Runs the R20.4 generated fixed-read PostgreSQL throughput profile."
    configureJmhMain(r20GeneratedFixedReadIncludes)
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r20.4-generated-fixed-read-throughput.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR20GeneratedFixedReadAllocation") {
    group = "verification"
    description = "Runs the R20.4 generated fixed-read PostgreSQL allocation profile."
    configureJmhMain(r20GeneratedFixedReadIncludes)
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-prof", "gc",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r20.4-generated-fixed-read-allocation.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR20GeneratedFixedReadLatency") {
    group = "verification"
    description = "Runs the R20.4 generated fixed-read PostgreSQL sample-time latency profile."
    configureJmhMain(r20GeneratedFixedReadIncludes)
    addDefaultJmhProfile(
        "-bm", "sample",
        "-tu", "ms",
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r20.4-generated-fixed-read-latency.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR20DslShapes") {
    group = "verification"
    description = "Runs the R20.5 DSL render/execute PostgreSQL throughput profile."
    configureJmhMain(r20DslShapesIncludes)
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r20.5-dsl-shapes-throughput.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR20DslShapesAllocation") {
    group = "verification"
    description = "Runs the R20.5 DSL render/execute PostgreSQL allocation profile."
    configureJmhMain(r20DslShapesIncludes)
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-prof", "gc",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r20.5-dsl-shapes-allocation.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR20DslShapesLatency") {
    group = "verification"
    description = "Runs the R20.5 DSL render/execute PostgreSQL sample-time latency profile."
    configureJmhMain(r20DslShapesIncludes)
    addDefaultJmhProfile(
        "-bm", "sample",
        "-tu", "ms",
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r20.5-dsl-shapes-latency.json").get().asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR23PostR22JavaRuntime") {
    group = "verification"
    description = "Runs the R23.2 post-R22 Java runtime PostgreSQL throughput profile."
    configureJmhMain(r23PostR22JavaRuntimeIncludes)
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r23.2-post-r22-java-runtime-throughput.json").get()
            .asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR23PostR22JavaRuntimeAllocation") {
    group = "verification"
    description = "Runs the R23.2 post-R22 Java runtime PostgreSQL allocation profile."
    configureJmhMain(r23PostR22JavaRuntimeIncludes)
    addDefaultJmhProfile(
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-prof", "gc",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r23.2-post-r22-java-runtime-allocation.json").get()
            .asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register<JavaExec>("jmhR23PostR22JavaRuntimeLatency") {
    group = "verification"
    description = "Runs the R23.2 post-R22 Java runtime PostgreSQL sample-time latency profile."
    configureJmhMain(r23PostR22JavaRuntimeIncludes)
    addDefaultJmhProfile(
        "-bm", "sample",
        "-tu", "ms",
        "-wi", "5",
        "-i", "10",
        "-f", "3",
        "-r", "1s",
        "-w", "1s",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/jmh/r23.2-post-r22-java-runtime-latency.json").get()
            .asFile.absolutePath
    )
    addConfiguredJmhExtraArgs()
    doFirst {
        layout.buildDirectory.dir("reports/jmh").get().asFile.mkdirs()
    }
}

tasks.register("verifyBenchmarkThresholds") {
    group = "verification"
    description = "Validates that the stable benchmark threshold file is present and well-formed."

    doLast {
        val thresholdFile = rootProject.file("docs/benchmarks/thresholds.json")
        if (!thresholdFile.isFile) {
            throw GradleException("Benchmark threshold file is missing: ${thresholdFile.relativeTo(rootDir)}")
        }

        val content = thresholdFile.readText()
        val requiredKeys = listOf("benchmark", "min_ops_per_second", "max_allocation_bytes_per_operation")
        val missingKeys = requiredKeys.filterNot { content.contains("\"$it\"") }
        if (missingKeys.isNotEmpty()) {
            throw GradleException("Benchmark threshold file is missing required keys: ${missingKeys.joinToString(", ")}")
        }
    }
}

tasks.named("check") {
    dependsOn("verifyBenchmarkThresholds")
}
