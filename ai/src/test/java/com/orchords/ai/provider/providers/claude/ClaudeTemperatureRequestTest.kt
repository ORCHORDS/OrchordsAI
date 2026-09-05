package com.orchords.ai.provider.providers.claude

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.ai.core.ReasoningLevel
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Verifies that the Claude request body emits or strips `temperature` /
 * `top_p` based on the route capability, while preserving the existing
 * reasoning-level guard (extended thinking rejects `temperature`).
 *
 * See issue #355.
 */
class ClaudeTemperatureRequestTest {
    private lateinit var provider: ClaudeProvider

    @Before
    fun setUp() {
        provider = ClaudeProvider(OkHttpClient())
    }

    @Test
    fun `default anthropic host emits both temperature and top_p on non-reasoning turn`() {
        val body = buildRequest(
            providerSetting = ProviderSetting.Claude(),
            reasoningLevel = ReasoningLevel.OFF,
        )
        assertEquals("0.7", body["temperature"]?.jsonPrimitive?.content)
        assertEquals("0.9", body["top_p"]?.jsonPrimitive?.content)
    }

    @Test
    fun `reasoning enabled strips temperature but keeps top_p`() {
        val body = buildRequest(
            providerSetting = ProviderSetting.Claude(),
            reasoningLevel = ReasoningLevel.HIGH,
        )
        assertNull("temperature must be stripped during extended thinking", body["temperature"])
        assertEquals("0.9", body["top_p"]?.jsonPrimitive?.content)
    }

    @Test
    fun `supportsTemperature false strips temperature on non-reasoning turn`() {
        val body = buildRequest(
            providerSetting = ProviderSetting.Claude(supportsTemperature = false),
            reasoningLevel = ReasoningLevel.OFF,
        )
        assertNull(body["temperature"])
        assertEquals("0.9", body["top_p"]?.jsonPrimitive?.content)
    }

    @Test
    fun `supportsTopP false strips top_p but keeps temperature`() {
        val body = buildRequest(
            providerSetting = ProviderSetting.Claude(supportsTopP = false),
            reasoningLevel = ReasoningLevel.OFF,
        )
        assertEquals("0.7", body["temperature"]?.jsonPrimitive?.content)
        assertNull(body["top_p"])
    }

    private fun buildRequest(
        providerSetting: ProviderSetting.Claude,
        reasoningLevel: ReasoningLevel,
    ): JsonObject {
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            ProviderSetting.Claude::class.java,
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        val params = TextGenerationParams(
            model = Model(modelId = "claude-sonnet-4-5"),
            reasoningLevel = reasoningLevel,
            temperature = 0.7f,
            topP = 0.9f,
        )
        return method.invoke(
            provider,
            providerSetting,
            listOf(UIMessage.user("hi")),
            params,
            false,
        ) as JsonObject
    }
}
