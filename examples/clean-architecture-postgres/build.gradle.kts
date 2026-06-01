description = "Clean Architecture PostgreSQL example for Mortar."

dependencies {
    implementation(project(":java:core"))
    implementation(project(":java:dialect-postgres"))
    implementation(project(":java:runtime-jdbc"))

    compileOnly(project(":java:processor"))
    annotationProcessor(project(":java:processor"))

    testImplementation(project(":java:testkit"))
    testImplementation("org.mockito:mockito-core:5.21.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
