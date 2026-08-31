plugins {
    `kotlin-dsl`
}

// Security pins for the transitive dependencies of the Android/Kotlin Gradle
// plugins used here (AGP's apksig pulls BouncyCastle; the tooling stack pulls
// jose4j, jdom2 and commons-lang3). This included build has its own
// configurations, so the root build's subprojects-wide forces do not reach it.
configurations.all {
    resolutionStrategy {
        force(
            // CVE-2026-53914: the version catalog remains on the latest stable
            // line while this included build resolves the patched Kotlin RC.
            "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20-RC2",
            "org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20-RC2",
            "org.bouncycastle:bcpkix-jdk18on:1.84",
            "org.bouncycastle:bcprov-jdk18on:1.84",
            "org.bitbucket.b_c:jose4j:0.9.6",
            "org.jdom:jdom2:2.0.6.1",
            "org.apache.commons:commons-lang3:3.18.0",
        )
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
