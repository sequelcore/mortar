description = "Spring Boot auto-configuration for Mortar."

dependencies {
    api(project(":java:core"))
    api(project(":java:dialect-postgres"))
    api(project(":java:runtime-jdbc"))

    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.5.14")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.14")
    compileOnly("org.springframework:spring-jdbc:6.2.9")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:3.5.14")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.14")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc:3.5.14")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator:3.5.14")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
