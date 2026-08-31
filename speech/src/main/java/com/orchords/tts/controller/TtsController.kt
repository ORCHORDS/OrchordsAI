package com.orchords.tts.controller

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.orchords.tts.model.PlaybackState
import com.orchords.tts.model.PlaybackStatus
import com.orchords.tts.model.TTSResponse
import com.orchords.tts.provider.TTSManager
import com.orchords.tts.provider.TTSProviderException
import com.orchords.tts.provider.TTSProviderSetting
import java.io.IOException
import java.util.UUID

private const val TAG = "TtsController"
private const val MAX_SYNTHESIS_ATTEMPTS = 3
private const val SYNTHESIS_RETRY_BASE_DELAY_MS = 500L

/**
 */
class TtsController internal constructor(
    private val audio: TtsAudioPlayer,
    private val synthesize: suspend (TTSProviderSetting, TtsChunk) -> TTSResponse,
    private val scope: CoroutineScope
) {
    constructor(context: Context, ttsManager: TTSManager) : this(
        audio = AudioPlayer(context),
        synthesize = TtsSynthesizer(ttsManager)::synthesize,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    private val chunker = TextChunker(maxChunkLength = 160)

    private var currentProvider: TTSProviderSetting? = null
    private var workerJob: Job? = null
    private var sessionGeneration = 0L
    private var isPaused = false

    private val queue = java.util.concurrent.ConcurrentLinkedDeque<TtsChunk>()
    private val allChunks: MutableList<TtsChunk> = mutableListOf()
    private var failedChunk: TtsChunk? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<UUID, kotlinx.coroutines.Deferred<TTSResponse>>()

    private val chunkDelayMs = 120L
    private val prefetchCount = 2

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentChunk = MutableStateFlow(0)
    val currentChunk: StateFlow<Int> = _currentChunk.asStateFlow()

    private val _totalChunks = MutableStateFlow(0)
    val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        scope.launch {
            audio.playbackState.collectLatest { audioState ->
                _playbackState.update { current ->
                    if (failedChunk != null) {
                        current
                    } else {
                        audioState.copy(
                            currentChunkIndex = _currentChunk.value,
                            totalChunks = _totalChunks.value,
                            skippedChunks = current.skippedChunks,
                            status = if (!_isAvailable.value) PlaybackStatus.Idle else audioState.status
                        )
                    }
                }
            }
        }
    }

    fun setProvider(provider: TTSProviderSetting?) {
        if (provider == currentProvider) return
        internalReset()
        currentProvider = provider
        _isAvailable.update { provider != null }
    }

    /**
     */
    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val provider = currentProvider
        if (provider == null) {
            _error.update { "No TTS provider selected" }
            return
        }

        val newChunks = chunker.split(text)
        if (newChunks.isEmpty()) return

        if (flush) {
            internalReset()
            allChunks.addAll(newChunks)
            queue.addAll(newChunks)
            _currentChunk.update { 0 }
        } else {
            val startIndex = (allChunks.lastOrNull()?.index ?: -1) + 1
            val remapped = newChunks.mapIndexed { i, c -> c.copy(index = startIndex + i) }
            allChunks.addAll(remapped)
            queue.addAll(remapped)
        }
        _totalChunks.update { queue.size }
        _error.update { null }

        _playbackState.update {
            it.copy(
                currentChunkIndex = _currentChunk.value,
                totalChunks = _totalChunks.value,
                status = PlaybackStatus.Buffering
            )
        }

        if (workerJob?.isActive != true) startWorker()
    }

    private fun internalReset() {
        // Reset current session while keeping provider availability
        sessionGeneration++
        workerJob?.cancel()
        workerJob = null
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Reset")) }
        cache.clear()
        failedChunk = null
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _error.update { null }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    fun pause() {
        isPaused = true
        audio.pause()
        _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
    }

    fun resume() {
        isPaused = false
        audio.resume()
        _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
    }

    fun fastForward(ms: Long = 5_000) {
        audio.seekBy(ms)
    }

    fun setSpeed(speed: Float) {
        audio.setSpeed(speed)
    }

    fun retryFailedChunk() {
        val chunk = failedChunk ?: return
        failedChunk = null
        _error.update { null }
        _playbackState.update {
            it.copy(
                status = PlaybackStatus.Buffering,
                errorMessage = null,
                failedChunkText = null,
                failedChunkIndex = null,
                failedChunkRetryable = false
            )
        }
        queue.addFirst(chunk)
        if (workerJob?.isActive != true) startWorker()
    }

    fun skipNext() {
        if (failedChunk != null) {
            failedChunk = null
            _error.update { null }
            _playbackState.update {
                it.copy(
                    status = PlaybackStatus.Buffering,
                    errorMessage = null,
                    failedChunkText = null,
                    failedChunkIndex = null,
                    failedChunkRetryable = false,
                    skippedChunks = it.skippedChunks + 1
                )
            }
            if (queue.isEmpty()) {
                _playbackState.update { it.copy(status = PlaybackStatus.Ended) }
            } else if (workerJob?.isActive != true) {
                startWorker()
            }
        } else if (queue.isNotEmpty()) {
            queue.poll()
        }
    }

    fun stop() {
        sessionGeneration++
        workerJob?.cancel()
        workerJob = null
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Stopped")) }
        cache.clear()
        failedChunk = null
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    fun dispose() {
        stop()
        scope.cancel()
        audio.release()
    }

    private fun startWorker() {
        val provider = currentProvider
        if (provider == null) {
            _error.update { "No TTS provider selected" }
            return
        }

        val generation = sessionGeneration
        lateinit var thisWorker: Job
        thisWorker = scope.launch(start = CoroutineStart.LAZY) {
            _isSpeaking.update { true }
            var processedCount = _currentChunk.value
            try {
                while (isActive) {
                    if (isPaused) {
                        delay(80)
                        continue
                    }

                    val chunk = queue.poll() ?: break

                    _playbackState.update {
                        it.copy(
                            currentChunkIndex = processedCount,
                            totalChunks = _totalChunks.value
                        )
                    }

                    prefetchNextChunks(chunk.index)

                    val response = try {
                        awaitOrCreate(chunk, provider)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val message = "Speech synthesis failed at chunk ${chunk.index + 1}"
                        logSpeechFailure(message, e)
                        failedChunk = chunk
                        _error.update { message }
                        _playbackState.update {
                            it.copy(
                                status = PlaybackStatus.Error,
                                currentChunkIndex = processedCount,
                                errorMessage = message,
                                failedChunkText = chunk.text,
                                failedChunkIndex = chunk.index,
                                failedChunkRetryable = e.isRetryableSynthesisError()
                            )
                        }
                        break
                    }

                    try {
                        audio.play(response)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val message = "Speech playback failed at chunk ${chunk.index + 1}"
                        logSpeechFailure(message, e)
                        failedChunk = chunk
                        _error.update { message }
                        _playbackState.update {
                            it.copy(
                                status = PlaybackStatus.Error,
                                currentChunkIndex = processedCount,
                                errorMessage = message,
                                failedChunkText = chunk.text,
                                failedChunkIndex = chunk.index,
                                failedChunkRetryable = false
                            )
                        }
                        break
                    }

                    if (queue.isNotEmpty()) delay(chunkDelayMs)

                    processedCount++
                    _currentChunk.update { processedCount }
                    _playbackState.update { it.copy(currentChunkIndex = processedCount) }
                }
            } finally {
                if (generation == sessionGeneration && workerJob === thisWorker) {
                    _isSpeaking.update { false }
                    workerJob = null
                    if (queue.isEmpty() && failedChunk == null) {
                        _playbackState.update { it.copy(status = PlaybackStatus.Ended) }
                    }
                }
            }
        }
        workerJob = thisWorker
        thisWorker.start()
    }

    private fun prefetchNextChunks(currentIndex: Int) {
        val provider = currentProvider ?: return
        val begin = currentIndex + 1
        val endExclusive = (begin + prefetchCount).coerceAtMost(allChunks.size)
        if (begin >= endExclusive) return

        for (i in begin until endExclusive) {
            val chunk = allChunks.getOrNull(i) ?: continue
            getOrCreateSynthesis(chunk, provider)
        }
    }

    private suspend fun awaitOrCreate(chunk: TtsChunk, provider: TTSProviderSetting): TTSResponse {
        val deferred = getOrCreateSynthesis(chunk, provider)
        return try {
            deferred.await()
        } catch (e: Exception) {
            cache.remove(chunk.id, deferred)
            throw e
        }
    }

    private fun getOrCreateSynthesis(
        chunk: TtsChunk,
        provider: TTSProviderSetting
    ): kotlinx.coroutines.Deferred<TTSResponse> {
        return cache.computeIfAbsent(chunk.id) {
            scope.async(Dispatchers.IO) {
                synthesizeWithRetry(provider, chunk)
            }
        }
    }

    private suspend fun synthesizeWithRetry(
        provider: TTSProviderSetting,
        chunk: TtsChunk
    ): TTSResponse {
        var attempt = 1
        while (true) {
            try {
                return synthesize(provider, chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isRetryableSynthesisError() || attempt >= MAX_SYNTHESIS_ATTEMPTS) throw e

                val retryDelayMs = SYNTHESIS_RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                Log.w(
                    TAG,
                    "Synthesis attempt $attempt/$MAX_SYNTHESIS_ATTEMPTS failed for chunk ${chunk.index}; " +
                        "retrying in ${retryDelayMs}ms",
                    e
                )
                delay(retryDelayMs)
                attempt++
            }
        }
    }
    // endregion
}

private fun logSpeechFailure(message: String, error: Exception) {
    try {
        Log.e(TAG, "$message (${error.javaClass.simpleName})")
    } catch (_: RuntimeException) {
        // android.util.Log is unavailable in local JVM tests.
    }
}

private fun Exception.isRetryableSynthesisError(): Boolean {
    return this is IOException || this is TTSProviderException && isRetryable
}
