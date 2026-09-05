package com.orchords.ai.provider.providers.google

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies that the Gemini request body emits or strips `temperature` /
 * `topP` based on the effective route capability, and enforces the
 * Gemini-specific cross-field constraint (topP omitted when temperature is
 * set unless the user explicitly opted in via supportsTopP = true).
 *
 * See issue #355.
 */
class GoogleTemperatureRequestTest {
    private lateinit var provider: GoogleProvider

    @Before
    fun setUp() {
        provider = GoogleProvider(OkHttpClient())
    }

    @Test
    fun `default emits topP when no temperature is present`() {
        // Default capability is permissive; without temperature, the cross-field
        // suppression does not apply.
        val gen = generationConfig(
            providerSetting = ProviderSetting.Google(),
            temperature = null,
            topP = 0.9f,
        )
        assertNull(gen["temperature"])
        assertEquals("0.9", gen["topP"]?.jsonPrimitive?.content)
    }

    @Test
    fun `default with both emits temperature but suppresses topP per Google docs`() {
        // Per https://ai.google.dev/api/generate-content, topP is rejected when
        // temperature is set. Default capability is permissive, but the
        // cross-field suppression at the call site drops topP unless the user
        // opted in via supportsTopP.
        val gen = generationConfig(
            providerSetting = ProviderSetting.Google(),
            temperature = 0.7f,
            topP = 0.9f,
        )
        assertEquals("0.7", gen["temperature"]?.jsonPrimitive?.content)
        assertTrue(
            "topP must be suppressed when temperature is set and supportsTopP is null",
            gen["topP"] == null,
        )
    }

    @Test
    fun `supportsTopP true forces topP alongside temperature`() {
        val gen = generationConfig(
            providerSetting = ProviderSetting.Google(supportsTopP = true),
            temperature = 0.7f,
            topP = 0.9f,
        )
        assertEquals("0.7", gen["temperature"]?.jsonPrimitive?.content)
        assertEquals("0.9", gen["topP"]?.jsonPrimitive?.content)
    }

    @Test
    fun `topP without temperature is emitted normally`() {
        val gen = generationConfig(
            providerSetting = ProviderSetting.Google(),
            temperature = null,
            topP = 0.9f,
        )
        assertNull(gen["temperature"])
        assertEquals("0.9", gen["topP"]?.jsonPrimitive?.content)
    }

    @Test
    fun `supportsTemperature false strips temperature but keeps topP`() {
        val gen = generationConfig(
            providerSetting = ProviderSetting.Google(supportsTemperature = false),
            temperature = 0.7f,
            topP = 0.9f,
        )
        assertNull(gen["temperature"])
        assertEquals("0.9", gen["topP"]?.jsonPrimitive?.content)
    }

    private fun generationConfig(
        providerSetting: ProviderSetting.Google,
        temperature: Float?,
        topP: Float?,
    ): JsonObject {
        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildCompletionRequestBody",
            ProviderSetting.Google::class.java,
            List::class.java,
            com.orchords.ai.provider.TextGenerationParams::class.java,
        )
        method.isAccessible = true
        val params = TextGenerationParams(
            model = Model(
                modelId = "gemini-2-5-pro",
                abilities = listOf(ModelAbility.REASONING),
            ),
            temperature = temperature,
            topP = topP,
        )
        val body = method.invoke(
            provider,
            providerSetting,
            listOf(UIMessage.user("hi")),
            params,
        ) as JsonObject
        return body["generationConfig"]!!.jsonObject
    }
}
