package com.orchords.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.orchords.tts.model.AudioChunk
import com.orchords.tts.model.AudioFormat
import com.orchords.tts.model.TTSRequest
import com.orchords.tts.provider.TTSProvider
import com.orchords.tts.provider.TTSProviderException
import com.orchords.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "StepTTSProvider"

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 *
 *
 *
 */
class StepTTSProvider : TTSProvider<TTSProviderSetting.Step> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Step,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("responseFormat", providerSetting.responseFormat)
            put("speed", providerSetting.speed)
            put("volume", providerSetting.volume)
            put("sampleRate", providerSetting.sampleRate)
            if (providerSetting.instruction.isNotBlank()) {
                put("instruction", providerSetting.instruction)
            }
        }

        Log.i(TAG, "generateSpeech: model=${providerSetting.model} voice=${providerSetting.voice} format=${providerSetting.responseFormat}")

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl.trimEnd('/')}/v1/audio/speech")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/octet-stream")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = runCatching { response.body?.string() }.getOrNull().orEmpty()
            throw TTSProviderException(
                message = "Step TTS request failed: HTTP ${response.code} ${response.message}. body=$errorBody",
                statusCode = response.code
            )
        }

        val audioBytes = response.body?.bytes()
            ?: throw Exception("Step TTS returned empty body")

        if (audioBytes.isEmpty()) {
            throw Exception("Step TTS returned 0 bytes")
        }

        val audioFormat = when (providerSetting.responseFormat.lowercase()) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            "pcm" -> AudioFormat.PCM
            "ogg" -> AudioFormat.OGG
            "opus" -> AudioFormat.OPUS
            "aac" -> AudioFormat.AAC
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = audioBytes,
                format = audioFormat,
                sampleRate = providerSetting.sampleRate,
                isLast = true,
                metadata = mapOf(
                    "provider" to "step",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice,
                    "responseFormat" to providerSetting.responseFormat,
                )
            )
        )
    }
}
