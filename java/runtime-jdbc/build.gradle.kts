description = "JDBC runtime adapter for executing rendered Mortar queries."

dependencies {
    api(project(":java:core"))

    testImplementation(project(":java:dialect-postgres"))
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testRuntimeOnly("org.postgresql:postgresql:42.7.11")
}
