description = "PostgreSQL infrastructure adapter for the Mortar public query corpus fixture."

dependencies {
    implementation(project(":examples:query-corpus-application"))
    implementation(project(":examples:query-corpus-domain"))
    implementation(project(":java:core"))
    implementation(project(":java:dialect-postgres"))
    implementation(project(":java:runtime-jdbc"))

    compileOnly(project(":java:processor"))
    annotationProcessor(project(":java:processor"))

    testImplementation(project(":java:testkit"))
    testImplementation("org.mockito:mockito-core:5.23.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
