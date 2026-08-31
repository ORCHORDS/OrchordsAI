plugins {
    `kotlin-dsl`
}

val isCodeQl = System.getenv("CODEQL_ACTION_VERSION") != null

// Security pins for the transitive dependencies of the Android/Kotlin Gradle
// plugins used here (AGP's apksig pulls BouncyCastle; the tooling stack pulls
// jose4j, jdom2 and commons-lang3). This included build has its own
// configurations, so the root build's subprojects-wide forces do not reach it.
configurations.all {
    resolutionStrategy {
        force(
            "org.bouncycastle:bcpkix-jdk18on:1.84",
            "org.bouncycastle:bcprov-jdk18on:1.84",
            "org.bitbucket.b_c:jose4j:0.9.6",
            "org.jdom:jdom2:2.0.6.1",
            "org.apache.commons:commons-lang3:3.18.0",
        )

        // CodeQL 2.26.4 supports Kotlin versions below 2.4.20. Confine this
        // override to CodeQL's fresh analysis runner; normal builds keep the
        // patched 2.4.20-RC2 toolchain from the shared version catalog.
        if (isCodeQl) {
            force(
                "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10",
                "org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10",
            )
        }
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "orchordsai.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "orchordsai.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
    }
}