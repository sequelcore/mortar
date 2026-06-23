description = "Spring Boot auto-configuration for Mortar."

val springBootVersion = "4.1.0"
val springFrameworkVersion = "7.0.8"

dependencies {
    api(project(":java:core"))
    api(project(":java:dialect-postgres"))
    api(project(":java:runtime-jdbc"))

    compileOnly("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:$springBootVersion")
    compileOnly("org.springframework:spring-jdbc:$springFrameworkVersion")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
