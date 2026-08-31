// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    // Security pins for the build/plugin classpath: the Android Gradle plugin
    // stack resolves vulnerable transitive versions of BouncyCastle (via
    // apksig), jose4j, jdom2 and commons-lang3 here. Project configurations
    // are covered by the subprojects-wide forces below, and build-logic pins
    // its own classpath, but the root buildscript classpath needs this block.
    configurations.classpath {
        resolutionStrategy {
            force(
                "org.bouncycastle:bcpkix-jdk18on:1.84",
                "org.bouncycastle:bcprov-jdk18on:1.84",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.jdom:jdom2:2.0.6.1",
                "org.apache.commons:commons-lang3:3.18.0",
            )
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// Security pins for transitive dependencies (Dependabot alerts).
// None of these artifacts are declared directly in the build; they arrive as
// transitive dependencies of the server/SDK stacks used by the JVM-targeting
// modules. Forces apply to every configuration of every module, and pinning a
// coordinate a configuration never resolves is a no-op. The pinned versions are
// the first patched releases required by the open GitHub security advisories.
subprojects {
    configurations.all {
        resolutionStrategy {
            force(
                "io.netty:netty-buffer:4.1.137.Final",
                "io.netty:netty-codec:4.1.137.Final",
                "io.netty:netty-codec-http:4.1.137.Final",
                "io.netty:netty-codec-http2:4.1.137.Final",
                "io.netty:netty-common:4.1.137.Final",
                "io.netty:netty-handler:4.1.137.Final",
                "io.netty:netty-handler-proxy:4.1.137.Final",
                "io.netty:netty-resolver:4.1.137.Final",
                "io.netty:netty-transport:4.1.137.Final",
                "io.netty:netty-transport-native-epoll:4.1.137.Final",
                "com.fasterxml.jackson.core:jackson-databind:2.22.1",
                "com.squareup.wire:wire-runtime:6.3.0",
                "com.squareup.wire:wire-runtime-jvm:6.3.0",
                "org.apache.commons:commons-lang3:3.18.0",
                "org.apache.httpcomponents:httpclient:4.5.13",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.bouncycastle:bcpkix-jdk18on:1.84",
                "org.bouncycastle:bcprov-jdk18on:1.84",
                "org.jdom:jdom2:2.0.6.1",
            )
        }
    }
}
