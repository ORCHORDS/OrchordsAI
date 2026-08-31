package com.orchords.orchordsai.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-policy test for the ORCHORDS loader branding contract:
 * the loader sound asset exists under its canonical name and is played only on
 * the loading false-to-true transition, the brand logo (dark/light variants)
 * renders in the app-icon-style loading indicator, the default spinner branch
 * is retained, and no user-supplied source filename leaks into app resources.
 *
 * All paths are module-relative literals resolved against the unit-test
 * working directory (the Gradle app module); nothing reads outside it.
 */
class LoaderBrandingPolicyTest {

    private fun moduleFile(relative: String): File {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main/res").isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }
        val file = moduleDir.resolve(relative).canonicalFile
        require(file.toPath().startsWith(moduleDir.toPath())) { "Path escapes app module: $relative" }
        return file
    }

    private fun source(relative: String): String = moduleFile(relative).readText()

    @Test
    fun `loader sound asset exists under canonical raw name`() {
        assertTrue(
            "src/main/res/raw/loader_start.wav must exist",
            moduleFile("src/main/res/raw/loader_start.wav").isFile,
        )
    }

    @Test
    fun `brand logo drawables exist for dark and light themes`() {
        assertTrue(
            "src/main/res/drawable/orchords_logo_white.png must exist",
            moduleFile("src/main/res/drawable/orchords_logo_white.png").isFile,
        )
        assertTrue(
            "src/main/res/drawable/orchords_logo_blue.png must exist",
            moduleFile("src/main/res/drawable/orchords_logo_blue.png").isFile,
        )
    }

    @Test
    fun `chat list preloads loader sound once and plays it only on loading start`() {
        val chatList = source("src/main/java/com/orchords/orchordsai/ui/pages/chat/ChatList.kt")
        assertTrue(
            "ChatList must preload R.raw.loader_start exactly once inside LaunchedEffect(Unit)",
            chatList.contains("LaunchedEffect(Unit)") &&
                countOccurrences(chatList, "soundEffectPlayer.preload(R.raw.loader_start)") == 1,
        )
        assertTrue(
            "ChatList must guard playback with if (loading) inside LaunchedEffect(loading) so it fires only on the false-to-true transition",
            chatList.contains("LaunchedEffect(loading)") &&
                chatList.contains("if (loading)") &&
                countOccurrences(chatList, "soundEffectPlayer.play(R.raw.loader_start)") == 1,
        )
    }

    @Test
    fun `app icon style indicator shows brand logo with theme variants and keeps default branch`() {
        val rabbitLoading = source("src/main/java/com/orchords/orchordsai/ui/components/ui/RabbitLoading.kt")
        assertTrue(
            "Loading indicator must select the white logo for dark theme",
            rabbitLoading.contains("isSystemInDarkTheme()") &&
                rabbitLoading.contains("R.drawable.orchords_logo_white"),
        )
        assertTrue(
            "Loading indicator must select the blue logo for light theme",
            rabbitLoading.contains("R.drawable.orchords_logo_blue"),
        )
        assertTrue(
            "Default (non icon-style) branch must keep ContainedLoadingIndicator",
            rabbitLoading.contains("ContainedLoadingIndicator"),
        )
    }

    @Test
    fun `app cold start plays the loader chime from Application onCreate and migration overlay uses the branded startup loader`() {
        val app = source("src/main/java/com/orchords/orchordsai/OrchordsAIApp.kt")
        assertTrue(
            "Cold start must start the PCM cue before dependency injection and other startup work",
            countOccurrences(app, "StartupSoundPlayer(this).playOnce(R.raw.loader_start)") == 1 &&
                app.indexOf("StartupSoundPlayer(this).playOnce(R.raw.loader_start)") < app.indexOf("        startKoin {"),
        )
        val activity = source("src/main/java/com/orchords/orchordsai/OrchordsAiActivity.kt")
        assertTrue(
            "The activity must no longer trigger the chime; it moved to the Application class",
            !activity.contains("R.raw.loader_start"),
        )
        val player = source("src/main/java/com/orchords/orchordsai/utils/StartupSoundPlayer.kt")
        assertTrue(
            "StartupSoundPlayer must use static PCM AudioTrack playback off the main thread, reject repeated starts, and release the track",
            player.contains("AudioTrack.MODE_STATIC") &&
                player.contains("AtomicBoolean(false)") &&
                player.contains("if (!started.compareAndSet(false, true)) return") &&
                player.contains("track.release()"),
        )
        assertTrue(
            "Migration overlay must render the branded startup loader instead of a bare spinner",
            activity.contains("OrchardsStartupLoadingIndicator(") &&
                !activity.contains("CircularProgressIndicator()"),
        )
        val startup = source("src/main/java/com/orchords/orchordsai/ui/components/ui/OrchordsStartupLoading.kt")
        assertTrue(
            "Startup loader must render the blue transparent brand dragon regardless of theme",
            startup.contains("R.drawable.orchords_logo_blue") &&
                !startup.contains("R.drawable.orchords_logo_white"),
        )
    }

    @Test
    fun `startup loader shows the wordmark first and fades the dragon in after`() {
        val startup = source("src/main/java/com/orchords/orchordsai/ui/components/ui/OrchordsStartupLoading.kt")
        val dragonEnter = startup.indexOf("dragonAlpha.animateTo(1f")
        val wordmarkExit = startup.indexOf("wordmarkAlpha.animateTo(0f")
        val dragonExit = startup.indexOf("dragonAlpha.animateTo(0f")
        val finished = startup.indexOf("onFinished()")
        assertTrue(
            "One coroutine must own dragon entry, wordmark exit, dragon exit, then main-window reveal",
            dragonEnter >= 0 && dragonEnter < wordmarkExit && wordmarkExit < dragonExit && dragonExit < finished,
        )
        assertTrue(
            "Image 1 starts visible and image 2 starts invisible",
            startup.contains("wordmarkAlpha = remember { Animatable(1f) }") &&
                startup.contains("dragonAlpha = remember { Animatable(0f) }"),
        )
        assertTrue(
            "Entrance sequence must be gated on the first rendered frame (withFrameNanos) so slow cold starts cannot consume it invisibly",
            startup.contains("withFrameNanos { }"),
        )
        assertTrue(
            "Both supplied transparent image resources must be used",
            startup.contains("R.drawable.orchords_wordmark_blue") &&
                startup.contains("R.drawable.orchords_logo_blue"),
        )
    }

    @Test
    fun `system splash shows the transparent blue dragon via the SplashScreen compat theme`() {
        assertTrue(
            "The wordmark-first system splash and supplied wordmark drawable must exist",
            moduleFile("src/main/res/drawable/orchords_splash_icon.png").isFile &&
                moduleFile("src/main/res/drawable/orchords_wordmark_blue.png").isFile,
        )
        val themes = source("src/main/res/values/themes.xml")
        assertTrue(
            "Theme.OrchordsAI.Starting must configure the branded splash icon and hand off to the app theme",
            themes.contains("Theme.OrchordsAI.Starting") &&
                themes.contains("windowSplashScreenAnimatedIcon\">@drawable/orchords_splash_icon") &&
                themes.contains("postSplashScreenTheme\">@style/Theme.OrchordsAI"),
        )
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(
            "The launcher activity must use Theme.OrchordsAI.Starting",
            manifest.contains("android:theme=\"@style/Theme.OrchordsAI.Starting\""),
        )
        val activity = source("src/main/java/com/orchords/orchordsai/OrchordsAiActivity.kt")
        assertTrue(
            "installSplashScreen() must run as the first statement of onCreate, before super.onCreate()",
            activity.indexOf("installSplashScreen()") in 0 until activity.indexOf("super.onCreate(savedInstanceState)"),
        )
        assertTrue(
            "Normal cold starts must hand off to the app-rendered wordmark-first and dragon-second overlay",
            activity.contains("mutableStateOf(savedInstanceState == null)") &&
                activity.contains("OrchardsStartupLoadingIndicator(") &&
                activity.contains("onFinished = { showStartup = false }"),
        )
    }

    @Test
    fun `no user-supplied source filename leaks into app resources or loader sources`() {
        val forbidden = listOf("kuzu420", "ad386762", "chatgpt image")
        val resDir = moduleFile("src/main/res")
        resDir.walkTopDown().forEach { file ->
            val lowerName = file.name.lowercase()
            forbidden.forEach { token ->
                assertTrue(
                    "Asset ${file.relativeTo(resDir)} must not carry the source filename token '$token'",
                    !lowerName.contains(token),
                )
            }
        }
        listOf(
            "src/main/java/com/orchords/orchordsai/ui/pages/chat/ChatList.kt",
            "src/main/java/com/orchords/orchordsai/ui/components/ui/RabbitLoading.kt",
            "src/main/java/com/orchords/orchordsai/ui/components/ui/OrchordsStartupLoading.kt",
            "src/main/java/com/orchords/orchordsai/OrchordsAiActivity.kt",
            "src/main/java/com/orchords/orchordsai/OrchordsAIApp.kt",
            "src/main/java/com/orchords/orchordsai/utils/StartupSoundPlayer.kt",
        ).forEach { relative ->
            val content = source(relative).lowercase()
            forbidden.forEach { token ->
                assertTrue(
                    "Source $relative must not reference the source filename token '$token'",
                    !content.contains(token),
                )
            }
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1
}
