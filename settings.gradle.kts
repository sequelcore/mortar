import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "mortar"

include(
    "java:core",
    "java:dialect-postgres",
    "java:runtime-jdbc",
    "java:spring-boot-starter",
    "java:processor",
    "java:testkit",
    "java:benchmarks",
    "editors:intellij",
    "examples:spring-boot-postgres"
)
