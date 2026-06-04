description = "Spring Boot auto-configuration for Mortar."

dependencies {
    api(project(":java:core"))
    api(project(":java:dialect-postgres"))
    api(project(":java:runtime-jdbc"))

    compileOnly("org.springframework.boot:spring-boot-autoconfigure:4.0.6")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:4.0.6")
    compileOnly("org.springframework:spring-jdbc:6.2.9")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:4.0.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.6")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc:4.0.6")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator:4.0.6")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
