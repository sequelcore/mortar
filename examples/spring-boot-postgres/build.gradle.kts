description = "Runnable Spring Boot PostgreSQL example for Mortar."

val springBootVersion = "4.1.0"

dependencies {
    implementation(project(":java:spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc:$springBootVersion")

    compileOnly(project(":java:processor"))
    annotationProcessor(project(":java:processor"))
    runtimeOnly("org.postgresql:postgresql:42.7.11")

    testImplementation(project(":java:testkit"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
