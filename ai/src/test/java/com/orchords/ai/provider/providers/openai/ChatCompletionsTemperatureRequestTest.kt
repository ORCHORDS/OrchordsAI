package com.orchords.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Verifies that the Chat-Completions request body emits or strips
 * `temperature` / `top_p` based on the effective route capability, not on a
 * static model denylist.
 *
 * See issue #355.
 */
class ChatCompletionsTemperatureRequestTest {
    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `gpt-4o on openai host emits both temperature and top_p`() {
        val body = buildRequest(
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-4o",
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
        )
        assertEquals("0.7", body["temperature"]?.jsonPrimitive?.content)
        assertEquals("0.9", body["top_p"]?.jsonPrimitive?.content)
    }

    @Test
    fun `o1-preview on openai host strips both under the default denylist fallback`() {
        val body = buildRequest(
            baseUrl = "https://api.openai.com/v1",
            modelId = "o1-preview",
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
        )
        assertNull(body["temperature"])
        assertNull(body["top_p"])
    }

    @Test
    fun `kimi-k2-5 on moonshot host strips both under the host-class denylist`() {
        val body = buildRequest(
            baseUrl = "https://api.moonshot.cn/v1",
            modelId = "kimi-k2-5",
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.moonshot.cn/v1"),
        )
        assertNull(body["temperature"])
        assertNull(body["top_p"])
    }

    @Test
    fun `supportsTemperature override beats model denylist for o1-preview`() {
        val body = buildRequest(
            baseUrl = "https://api.openai.com/v1",
            modelId = "o1-preview",
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.openai.com/v1",
                supportsTemperature = true,
            ),
        )
        assertEquals("0.7", body["temperature"]?.jsonPrimitive?.content)
        assertNull(body["top_p"])
    }

    private fun buildRequest(
        baseUrl: String,
        modelId: String,
        providerSetting: ProviderSetting.OpenAI,
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
            temperature = 0.7f,
            topP = 0.9f,
        )
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true,
        ) as JsonObject
    }
}
