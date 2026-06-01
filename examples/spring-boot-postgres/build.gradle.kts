description = "Runnable Spring Boot PostgreSQL example for Mortar."

dependencies {
    implementation(project(":java:spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc:3.5.14")

    compileOnly(project(":java:processor"))
    annotationProcessor(project(":java:processor"))
    runtimeOnly("org.postgresql:postgresql:42.7.11")

    testImplementation(project(":java:testkit"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.14")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
