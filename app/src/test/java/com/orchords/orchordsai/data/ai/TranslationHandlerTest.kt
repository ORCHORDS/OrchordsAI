package com.orchords.orchordsai.data.ai

import kotlinx.serialization.json.JsonPrimitive
import com.orchords.ai.provider.CustomBody
import com.orchords.ai.provider.CustomHeader
import com.orchords.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TranslationHandlerTest {
    @Test
    fun `translation params preserve model custom headers and bodies`() {
        val headers = listOf(CustomHeader(name = "X-Gateway", value = "required"))
        val bodies = listOf(CustomBody(key = "gateway_mode", value = JsonPrimitive("strict")))
        val model = Model(
            modelId = "custom-translation-model",
            customHeaders = headers,
            customBodies = bodies,
        )

        val params = translationGenerationParams(model)

        assertEquals(headers, params.customHeaders)
        assertEquals(bodies, params.customBody)
    }

    @Test
    fun `translation specific bodies are appended without dropping model bodies`() {
        val modelBody = CustomBody(key = "gateway_mode", value = JsonPrimitive("strict"))
        val translationBody = CustomBody(key = "translation_options", value = JsonPrimitive("target"))
        val model = Model(
            modelId = "custom-translation-model",
            customBodies = listOf(modelBody),
        )

        val params = translationGenerationParams(
            model = model,
            translationBodies = listOf(translationBody),
        )

        assertEquals(listOf(modelBody, translationBody), params.customBody)
    }

    @Test
    fun `translation owned body keys cannot silently override model configuration`() {
        val model = Model(
            modelId = "custom-translation-model",
            customBodies = listOf(
                CustomBody(key = "translation_options", value = JsonPrimitive("custom"))
            ),
        )

        try {
            translationGenerationParams(
                model = model,
                translationBodies = listOf(
                    CustomBody(key = "translation_options", value = JsonPrimitive("required"))
                ),
            )
            fail("Expected conflicting translation body key to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: translation-owned fields must not be silently overridden.
        }
    }
}
