package com.orchords.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ChatCompletionsMaxTokensRequestTest {
    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `o-series model sends max_completion_tokens instead of max_tokens`() {
        val body = buildRequest(modelId = "o4-mini", maxTokens = 512)

        assertEquals(512, body["max_completion_tokens"]?.jsonPrimitive?.intOrNull)
        assertNull(body["max_tokens"])
    }

    @Test
    fun `GPT-5 model sends max_completion_tokens instead of max_tokens`() {
        val body = buildRequest(modelId = "gpt-5", maxTokens = 512)

        assertEquals(512, body["max_completion_tokens"]?.jsonPrimitive?.intOrNull)
        assertNull(body["max_tokens"])
    }

    @Test
    fun `gpt-5-chat keeps legacy max_tokens`() {
        val body = buildRequest(modelId = "gpt-5-chat", maxTokens = 512)

        assertEquals(512, body["max_tokens"]?.jsonPrimitive?.intOrNull)
        assertNull(body["max_completion_tokens"])
    }

    @Test
    fun `non reasoning model keeps legacy max_tokens`() {
        val body = buildRequest(modelId = "gpt-4o", maxTokens = 512)

        assertEquals(512, body["max_tokens"]?.jsonPrimitive?.intOrNull)
        assertNull(body["max_completion_tokens"])
    }

    @Test
    fun `null maxTokens omits both keys`() {
        val body = buildRequest(modelId = "gpt-5", maxTokens = null)

        assertNull(body["max_tokens"])
        assertNull(body["max_completion_tokens"])
    }

    private fun buildRequest(
        modelId: String,
        maxTokens: Int?,
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
            abilities = emptyList(),
        )
        val params = TextGenerationParams(
            model = model,
            maxTokens = maxTokens,
        )
        val providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1")
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true,
        ) as JsonObject
    }
}
