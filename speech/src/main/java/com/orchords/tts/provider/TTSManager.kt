package com.orchords.tts.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow
import com.orchords.tts.model.AudioChunk
import com.orchords.tts.model.TTSRequest
import com.orchords.tts.provider.providers.ElevenLabsTTSProvider
import com.orchords.tts.provider.providers.FishAudioTTSProvider
import com.orchords.tts.provider.providers.GeminiTTSProvider
import com.orchords.tts.provider.providers.GroqTTSProvider
import com.orchords.tts.provider.providers.MiMoTTSProvider
import com.orchords.tts.provider.providers.MiniMaxTTSProvider
import com.orchords.tts.provider.providers.OpenAITTSProvider
import com.orchords.tts.provider.providers.QwenTTSProvider
import com.orchords.tts.provider.providers.StepTTSProvider
import com.orchords.tts.provider.providers.SystemTTSProvider
import com.orchords.tts.provider.providers.XAITTSProvider

class TTSManager(private val context: Context) {
    private val openAIProvider = OpenAITTSProvider()
    private val geminiProvider = GeminiTTSProvider()
    private val systemProvider = SystemTTSProvider()
    private val miniMaxProvider = MiniMaxTTSProvider()
    private val qwenProvider = QwenTTSProvider()
    private val groqProvider = GroqTTSProvider()
    private val xaiProvider = XAITTSProvider()
    private val miMoProvider = MiMoTTSProvider()
    private val stepProvider = StepTTSProvider()
    private val elevenLabsProvider = ElevenLabsTTSProvider()
    private val fishAudioProvider = FishAudioTTSProvider()

    fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest
    ): Flow<AudioChunk> {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Gemini -> geminiProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.SystemTTS -> systemProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.MiniMax -> miniMaxProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Qwen -> qwenProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Groq -> groqProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.XAI -> xaiProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.MiMo -> miMoProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.FishAudio -> fishAudioProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Step -> stepProvider.generateSpeech(context, providerSetting, request)
        }
    }

    /**
     */
    fun getPromptGuidance(providerSetting: TTSProviderSetting): String {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.promptGuidance
            is TTSProviderSetting.Gemini -> geminiProvider.promptGuidance
            is TTSProviderSetting.SystemTTS -> systemProvider.promptGuidance
            is TTSProviderSetting.MiniMax -> miniMaxProvider.promptGuidance
            is TTSProviderSetting.Qwen -> qwenProvider.promptGuidance
            is TTSProviderSetting.Groq -> groqProvider.promptGuidance
            is TTSProviderSetting.XAI -> xaiProvider.promptGuidance
            is TTSProviderSetting.MiMo -> miMoProvider.promptGuidance
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.promptGuidance
            is TTSProviderSetting.FishAudio -> fishAudioProvider.promptGuidance
            is TTSProviderSetting.Step -> stepProvider.promptGuidance
        }
    }
}
