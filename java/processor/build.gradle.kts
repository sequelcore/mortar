description = "Mortar annotation processor for generated Java metamodel sources."

dependencies {
    api(project(":java:core"))

    testImplementation(project(":java:runtime-jdbc"))
    testImplementation(gradleTestKit())
}
