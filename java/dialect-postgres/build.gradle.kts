description = "PostgreSQL SQL renderer for Mortar query plans."

dependencies {
    api(project(":java:core"))

    testImplementation(project(":java:testkit"))
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testRuntimeOnly("org.postgresql:postgresql:42.7.11")
}
