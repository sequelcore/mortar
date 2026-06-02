import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.intellij.platform")
}

description = "Mortar IntelliJ plugin"
version = rootProject.version

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.2")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    projectName.set("Mortar")
    buildSearchableOptions.set(false)

    pluginConfiguration {
        id.set("dev.mortar")
        name.set("Mortar")
        version.set(rootProject.version.toString())
        description.set(providers.provider { file("marketplace/description.html").readText() })
        changeNotes.set(providers.provider { file("marketplace/change-notes.html").readText() })
        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set(provider { null })
        }
        vendor {
            name.set("Sequel")
            url.set("https://github.com/sequelcore/mortar")
        }
    }

    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
        channels.set(listOf("default"))
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    enabled = false
}
