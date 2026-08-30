package com.orchords.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Moonshot (api.moonshot.cn) thinking.keep handling:
 * - K2.6 kept-thinking via thinking.keep = "all" (#1586)
 */
class ChatCompletionsMoonshotRequestTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    // Helper to invoke private buildChatCompletionRequest via reflection
    private fun buildRequest(
        modelId: String,
        reasoningLevel: ReasoningLevel,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        val model = Model(
            modelId = modelId,
            abilities = listOf(ModelAbility.REASONING)
        )
        val params = TextGenerationParams(
            model = model,
            reasoningLevel = reasoningLevel,
        )
        val providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.moonshot.cn/v1")
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true
        ) as JsonObject
    }

    @Test
    fun `k2_6 sends thinking keep all when reasoning enabled`() {
        val body = buildRequest("kimi-k2.6", ReasoningLevel.HIGH)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertEquals("all", thinking?.get("keep")?.jsonPrimitive?.content)
    }

    @Test
    fun `k2_6 omits keep when reasoning disabled`() {
        val body = buildRequest("kimi-k2.6", ReasoningLevel.OFF)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("disabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertFalse(thinking?.containsKey("keep") == true)
    }

    @Test
    fun `k2_5 never sends keep`() {
        val body = buildRequest("kimi-k2.5", ReasoningLevel.HIGH)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertFalse(thinking?.containsKey("keep") == true)
    }
}
