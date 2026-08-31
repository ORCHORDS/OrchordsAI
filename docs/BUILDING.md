# Building ORCHORDS AI

Use JDK 17, Android SDK, Git submodules, Node.js 22, and pnpm 11. Initialize submodules and run `pnpm install --frozen-lockfile` in `web-ui`. Standard checks are `./gradlew :app:assembleDebug`, `./gradlew testDebugUnitTest`, and `./gradlew lintDebug`. Keep `local.properties` and signing data private.
