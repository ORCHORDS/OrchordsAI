# Building OrchordsAI

## Prerequisites

- **JDK 17** (Temurin recommended)
- **Android SDK** with:
  - `platforms;android-37`
  - `build-tools;37.0.0`
- ~6 GB free disk for Gradle caches and native toolchains
- On Windows, an NTFS drive and `JAVA_HOME` pointing at JDK 17

The Gradle wrapper (`gradlew`) downloads Gradle and all dependencies on first
run; AGP, Kotlin, and KSP versions are pinned in `gradle/libs.versions.toml`.

## Setup

```bash
# point the build at your SDK install (usually auto-detected)
echo "sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk" > local.properties
```

`local.properties` is gitignored and must never be committed.

## Build and test

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:test                 # unit tests
./gradlew :app:lint                 # Android lint
./gradlew :app:assembleRelease      # release APK (debug-signed unless keystore configured)
./gradlew :app:bundleRelease        # AAB
```

Artifacts land in `app/build/outputs/`.

## Web UI (optional)

The embedded web UI is a React app in `web-ui/`. It is rebuilt and bundled
into the `:web` module automatically; to iterate on it directly:

```bash
cd web-ui && npm install && npm run dev
```

## IDE

Open the repository root in Android Studio (Koala or newer) and let it sync
the Gradle project. Run the `app` configuration on an API 26+ device.
