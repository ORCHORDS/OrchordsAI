package com.orchords.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.orchords.asr.ASRController
import com.orchords.asr.ASRProviderSetting
import com.orchords.asr.ASRState
import com.orchords.asr.ASRStatus
import com.orchords.asr.appendAmplitude
import com.orchords.asr.calculateRmsAmplitude
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import okio.BufferedSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections

private const val TAG = "StepASR"

private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

private const val MIN_SEGMENT_BYTES = 3200

private const val MAX_RETRY = 3

/**
 *
 * - JSON body: audio.data=<pcm base64> / audio.input.transcription / audio.input.format
 *
 */
class StepASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.Step
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    private var flushJob: Job? = null

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var segmentStartElapsedMs = 0L
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
        }
        completedTranscripts.clear()
        flushJob = null

        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true
            )
        }
        startRecorder()
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }

        scope.launch(Dispatchers.IO) {
            try {
                flushJob?.join()
                flushSegment()
            } catch (e: Exception) {
                Log.e(TAG, "Final flush failed", e)
                setError(e.message ?: "Step ASR final flush failed")
            } finally {
                _state.update { it.copy(status = ASRStatus.Idle) }
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        flushJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                val segmentMs = provider.segmentDurationSec.coerceAtLeast(0) * 1000L
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        val shouldFlush = synchronized(bufferLock) {
                            currentBuffer.write(buffer, 0, read)
                            if (segmentMs <= 0) {
                                currentBuffer.size() >= MAX_SEGMENT_BYTES
                            } else {
                                val elapsed = SystemClock.elapsedRealtime() - segmentStartElapsedMs
                                currentBuffer.size() >= MAX_SEGMENT_BYTES || elapsed >= segmentMs
                            }
                        }

                        if (shouldFlush) {
                            triggerFlush()
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun triggerFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { Log.e(TAG, "Segment flush failed", it) }
        }
    }

    /**
     *
     */
    private suspend fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
            bytes
        }

        if (pcmBytes.size < MIN_SEGMENT_BYTES) {
            Log.d(TAG, "Skip flush: PCM too short (${pcmBytes.size} bytes)")
            return
        }

        val transcription = JSONObject()
            .put("model", provider.model)
            .put("enable_itn", provider.enableItn)
            .put("enable_timestamp", provider.enableTimestamp)
        if (provider.language.isNotBlank()) {
            transcription.put("language", provider.language)
        }
        if (provider.hotwords.isNotEmpty()) {
            transcription.put("hotwords", JSONArray(provider.hotwords))
        }

        val body = JSONObject()
            .put(
                "audio",
                JSONObject()
                    .put("data", Base64.encodeToString(pcmBytes, Base64.NO_WRAP))
                    .put(
                        "input",
                        JSONObject()
                            .put("transcription", transcription)
                            .put(
                                "format",
                                JSONObject()
                                    .put("type", "pcm")
                                    .put("codec", "pcm_s16le")
                                    .put("rate", provider.sampleRate)
                                    .put("bits", 16)
                                    .put("channel", 1)
                            )
                    )
            )

        val request = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/v1/audio/asr/sse")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val text = executeWithRetry(request).trim()

        if (text.isNotEmpty()) {
            completedTranscripts.add(text)
            publishTranscript()
        }
    }

    private suspend fun executeWithRetry(request: Request): String {
        var lastError: IOException? = null
        for (attempt in 1..MAX_RETRY) {
            try {
                return withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IOException("Step ASR HTTP ${resp.code}: ${resp.body.string()}")
                        }
                        parseSseTranscript(resp.body.source())
                    }
                }
            } catch (e: IOException) {
                lastError = e
                Log.w(TAG, "flushSegment attempt $attempt/$MAX_RETRY failed: ${e.message}")
                if (attempt < MAX_RETRY) {
                    kotlinx.coroutines.delay(300L * attempt)
                }
            }
        }
        throw lastError ?: IOException("Step ASR request failed")
    }

    private fun parseSseTranscript(source: BufferedSource): String {
        val transcript = StringBuilder()
        var eventType: String? = null
        val dataLines = mutableListOf<String>()

        fun dispatchEvent(): Boolean {
            if (eventType == null && dataLines.isEmpty()) return false
            val data = dataLines.joinToString("\n")
            val shouldStop = handleSseEvent(eventType, data, transcript)
            eventType = null
            dataLines.clear()
            return shouldStop
        }

        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (dispatchEvent()) break
                continue
            }
            if (line.startsWith(":")) continue

            val separatorIndex = line.indexOf(':')
            val field = if (separatorIndex == -1) line else line.substring(0, separatorIndex)
            val value = if (separatorIndex == -1) {
                ""
            } else {
                line.substring(separatorIndex + 1).removePrefix(" ")
            }
            when (field) {
                "event" -> eventType = value
                "data" -> dataLines.add(value)
            }
        }
        dispatchEvent()
        return transcript.toString().trim()
    }

    private fun handleSseEvent(
        eventType: String?,
        data: String,
        transcript: StringBuilder
    ): Boolean {
        if (data == "[DONE]") return true

        val json = runCatching { JSONObject(data) }.getOrNull()
        val type = eventType
            ?.takeIf { it.isNotBlank() }
            ?: json?.optString("type")?.takeIf { it.isNotBlank() }

        return when (type) {
            "transcript.text.delta" -> {
                transcript.append(extractTranscriptText(json, if (json == null) data else ""))
                false
            }

            "transcript.text.done" -> {
                val finalText = extractTranscriptText(json, "")
                if (finalText.isNotBlank()) {
                    transcript.clear()
                    transcript.append(finalText)
                }
                true
            }

            "error" -> {
                throw IOException("Step ASR error: ${extractErrorMessage(json, data)}")
            }

            else -> {
                val text = extractTranscriptText(json, "")
                if (text.isNotBlank()) {
                    transcript.append(text)
                }
                false
            }
        }
    }

    private fun extractTranscriptText(json: JSONObject?, fallback: String): String {
        if (json == null) return fallback
        val directKeys = listOf("delta", "text", "content", "transcript")
        for (key in directKeys) {
            val value = json.opt(key) ?: continue
            if (value is JSONObject) {
                val nestedValue = extractTranscriptText(value, "")
                if (nestedValue.isNotBlank()) return nestedValue
            } else {
                val text = value.toString()
                if (text.isNotBlank()) return text
            }
        }

        val nestedKeys = listOf("data", "result", "transcript")
        for (key in nestedKeys) {
            val nested = json.optJSONObject(key) ?: continue
            val value = extractTranscriptText(nested, "")
            if (value.isNotBlank()) return value
        }
        return fallback
    }

    private fun extractErrorMessage(json: JSONObject?, fallback: String): String {
        if (json == null) return fallback
        val error = json.optJSONObject("error")
        if (error != null) {
            val message = error.optString("message", "")
            if (message.isNotBlank()) return message
        }
        return json.optString("message", fallback)
    }

    private fun publishTranscript() {
        val transcript = completedTranscripts
            .filter { it.isNotBlank() }
            .joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
