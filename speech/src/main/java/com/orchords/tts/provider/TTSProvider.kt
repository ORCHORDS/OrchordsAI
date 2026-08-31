package com.orchords.tts.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow
import com.orchords.tts.model.AudioChunk
import com.orchords.tts.model.TTSRequest

interface TTSProvider<T : TTSProviderSetting> {
    fun generateSpeech(
        context: Context,
        providerSetting: T,
        request: TTSRequest
    ): Flow<AudioChunk>

    /**
     *
     *
     */
    val promptGuidance: String
        get() = ""
}
