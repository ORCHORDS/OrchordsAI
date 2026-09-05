package com.orchords.ai.provider.providers.openai

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
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
 * Verifies that the Responses-API request body emits or strips
 * `temperature` / `top_p` based on the effective route capability.
 *
 * See issue #355.
 */
class ResponseApiTemperatureRequestTest {
    private lateinit var api: ResponseAPI

    @Before
    fun setUp() {
        api = ResponseAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `gpt-4o on openai host emits both temperature and top_p`() {
        val body = api.buildRequestBody(
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
            messages = listOf(UIMessage.user("hi")),
            params = TextGenerationParams(
                model = model("gpt-4o"),
                temperature = 0.7f,
                topP = 0.9f,
            ),
            stream = false,
        )
        assertEquals("0.7", body["temperature"]?.jsonPrimitive?.content)
        assertEquals("0.9", body["top_p"]?.jsonPrimitive?.content)
    }

    @Test
    fun `o1-preview on openai host strips both under the default denylist fallback`() {
        val body = api.buildRequestBody(
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
            messages = listOf(UIMessage.user("hi")),
            params = TextGenerationParams(
                model = model("o1-preview"),
                temperature = 0.7f,
                topP = 0.9f,
            ),
            stream = false,
        )
        assertNull(body["temperature"])
        assertNull(body["top_p"])
    }

    @Test
    fun `supportsTemperature override beats model denylist for o1-preview`() {
        val body = api.buildRequestBody(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.openai.com/v1",
                supportsTemperature = true,
            ),
            messages = listOf(UIMessage.user("hi")),
            params = TextGenerationParams(
                model = model("o1-preview"),
                temperature = 0.7f,
                topP = 0.9f,
            ),
            stream = false,
        )
        assertEquals("0.7", body["temperature"]?.jsonPrimitive?.content)
        assertNull(body["top_p"])
    }

    @Test
    fun `request body keeps canonical Responses keys alongside temperature`() {
        // Sanity: model + stream + temperature coexist in the body.
        val body: JsonElement = api.buildRequestBody(
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
            messages = listOf(UIMessage.user("hi")),
            params = TextGenerationParams(
                model = model("gpt-4o"),
                temperature = 0.5f,
            ),
            stream = false,
        )
        val obj = body.jsonObject
        assertEquals("gpt-4o", obj["model"]?.jsonPrimitive?.content)
        assertEquals("0.5", obj["temperature"]?.jsonPrimitive?.content)
    }

    private fun model(modelId: String) = Model(
        modelId = modelId,
        abilities = listOf(ModelAbility.REASONING),
    )
}
