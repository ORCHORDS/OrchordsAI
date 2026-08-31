package com.orchords.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationResult
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.StreamChunk
import com.orchords.ai.ui.UIMessage

interface OpenAIImpl {
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult

    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk>
}
