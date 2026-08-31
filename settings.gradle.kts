pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.itextsupport.com/android")
    }
    resolutionStrategy {
        // CodeQL 2.26.4 cannot instrument Kotlin 2.4.20-RC2 yet. Keep the
        // patched project toolchain for normal builds, but use the newest
        // CodeQL-supported Kotlin line only inside CodeQL's ephemeral build.
        val isCodeQl = System.getenv("CODEQL_ACTION_VERSION") != null
        val codeQlKotlinVersion = "2.4.10"

        eachPlugin {
            if (isCodeQl && requested.id.id.startsWith("org.jetbrains.kotlin.")) {
                useVersion(codeQlKotlinVersion)
            }
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
    }
}

rootProject.name = "OrchordsAI"
include(":app")
include(":highlight")
include(":ai")
include(":search")
include(":speech")
include(":common")
include(":document")
include(":web")
include(":material3")
include(":workspace")
include(":app:baselineprofile")
include(":videogen")
include(":oauth")