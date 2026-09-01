plugins {
    id("orchordsai.android.library.compose")
}

android {
    namespace = "com.orchords.material3"
    sourceSets {
        named("main") {
            kotlin.srcDir("material-color-utilities/kotlin")
        }
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        check(requested.name != "material-color-utilities") {
            "material-color-utilities must come only from the pinned Git submodule"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
