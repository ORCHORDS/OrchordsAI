package com.orchords.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.ai.core.ReasoningLevel
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChatCompletionsDeepSeekRequestTest {
    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `opencode DeepSeek V4 maps xhigh to max in serialized request`() {
        val body = buildRequest(
            baseUrl = "https://opencode.ai/zen/v1",
            modelId = "deepseek-v4-flash",
            reasoningLevel = ReasoningLevel.XHIGH,
        )

        assertEquals("max", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `opencode non DeepSeek model preserves xhigh in serialized request`() {
        val body = buildRequest(
            baseUrl = "https://opencode.ai/zen/v1",
            modelId = "provider-model-that-supports-xhigh",
            reasoningLevel = ReasoningLevel.XHIGH,
        )

        assertEquals("xhigh", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    private fun buildRequest(
        baseUrl: String,
        modelId: String,
        reasoningLevel: ReasoningLevel,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        val model = Model(
            modelId = modelId,
            abilities = listOf(ModelAbility.REASONING),
        )
        val params = TextGenerationParams(
            model = model,
            reasoningLevel = reasoningLevel,
        )
        val providerSetting = ProviderSetting.OpenAI(baseUrl = baseUrl)
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true,
        ) as JsonObject
    }
}
