package com.orchords.tts.controller

import com.orchords.tts.model.AudioFormat
import com.orchords.tts.model.PlaybackState
import com.orchords.tts.model.PlaybackStatus
import com.orchords.tts.model.TTSResponse
import com.orchords.tts.provider.TTSProviderSetting
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsControllerTest {
    @Test
    fun cancelledWorkerCannotOverwriteNewSessionState() = runBlocking {
        val audio = FakeAudioPlayer()
        val firstSynthesis = kotlinx.coroutines.CompletableDeferred<Unit>()
        val controller = controller(audio) { provider, chunk ->
            if (provider.name == "first") firstSynthesis.await()
            response(chunk.index)
        }
        controller.setProvider(provider("first"))
        controller.speak(longText("old"))
        controller.setProvider(provider("second"))
        controller.speak("new")

        await { controller.playbackState.value.status == PlaybackStatus.Ended }
        firstSynthesis.complete(Unit)
        delay(20)

        assertEquals(PlaybackStatus.Ended, controller.playbackState.value.status)
        assertEquals(1, controller.playbackState.value.currentChunkIndex)
        controller.dispose()
    }

    @Test
    fun switchingConfiguredProviderCancelsOldSynthesisAndPrefetch() = runBlocking {
        val calls = mutableListOf<String>()
        val oldStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val controller = controller(FakeAudioPlayer()) { provider, chunk ->
            calls += provider.name
            if (provider.name == "old") {
                oldStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
            response(chunk.index)
        }
        controller.setProvider(provider("old"))
        controller.speak(longText("old"))
        oldStarted.await()

        controller.setProvider(provider("new"))
        controller.speak("replacement")
        await { controller.playbackState.value.status == PlaybackStatus.Ended }
        val oldCallCount = calls.count { it == "old" }
        delay(50)

        assertEquals(oldCallCount, calls.count { it == "old" })
        assertTrue(calls.contains("new"))
        controller.dispose()
    }

    @Test
    fun playbackFailureInterruptsWithoutProgressOrAutomaticContinuation() = runBlocking {
        val audio = FakeAudioPlayer(failAtPlay = 1)
        val controller = controller(audio) { _, chunk -> response(chunk.index) }
        controller.setProvider(provider("provider"))
        controller.speak(longText("chunk"))

        await { controller.playbackState.value.status == PlaybackStatus.Error }

        assertEquals(0, controller.playbackState.value.currentChunkIndex)
        assertEquals(0, controller.currentChunk.value)
        assertEquals(0, controller.playbackState.value.failedChunkIndex)
        assertFalse(controller.playbackState.value.failedChunkRetryable)
        assertEquals(1, audio.playCount)
        delay(50)
        assertEquals(1, audio.playCount)
        controller.dispose()
    }

    @Test
    fun playbackFailureCanBeRetriedFromSameChunk() = runBlocking {
        val audio = FakeAudioPlayer(failAtPlay = 1)
        val controller = controller(audio) { _, chunk -> response(chunk.index) }
        controller.setProvider(provider("provider"))
        controller.speak("retry me")
        await { controller.playbackState.value.status == PlaybackStatus.Error }

        controller.retryFailedChunk()
        await { controller.playbackState.value.status == PlaybackStatus.Ended }

        assertEquals(2, audio.playCount)
        assertEquals(1, controller.playbackState.value.currentChunkIndex)
        controller.dispose()
    }

    private fun controller(
        audio: TtsAudioPlayer,
        synthesize: suspend (TTSProviderSetting, TtsChunk) -> TTSResponse
    ) = TtsController(
        audio = audio,
        synthesize = synthesize,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    private fun provider(name: String) = TTSProviderSetting.SystemTTS(name = name)

    private fun response(index: Int) = TTSResponse(
        audioData = byteArrayOf(index.toByte()),
        format = AudioFormat.MP3
    )

    private fun longText(prefix: String) = (1..4).joinToString(" ") { "$prefix-${"x".repeat(160)}." }

    private suspend fun await(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(10)
        }
        error("Condition was not reached")
    }
}

private class FakeAudioPlayer(
    private val failAtPlay: Int? = null
) : TtsAudioPlayer {
    private val state = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = state
    var playCount = 0

    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() { state.value = PlaybackState(status = PlaybackStatus.Idle) }
    override fun clear() = Unit
    override fun release() = Unit
    override fun seekBy(ms: Long) = Unit
    override fun setSpeed(speed: Float) = Unit
    override suspend fun play(response: TTSResponse) {
        playCount++
        if (playCount == failAtPlay) throw IOException("audio decoder rejected private content")
        state.value = PlaybackState(status = PlaybackStatus.Ended)
    }
}
