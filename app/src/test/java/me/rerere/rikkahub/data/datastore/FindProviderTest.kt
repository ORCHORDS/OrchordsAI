package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class FindProviderTest {
    @Test
    fun `findProvider returns base provider when model has no overwrite`() {
        val model = Model(modelId = "gpt-image-2", displayName = "gpt-image-2", type = ModelType.IMAGE)
        val provider = ProviderSetting.OpenAI(
            name = "OpenAI",
            models = listOf(model),
        )

        val resolved = model.findProvider(listOf(provider))

        assertEquals(provider.id, resolved?.id)
        assertEquals("OpenAI", resolved?.name)
    }

    @Test
    fun `findProvider returns overwrite instead of looking it up in settings providers`() {
        val overwriteKey = "fixture-override-value"
        val baseKey = "fixture-base-value"
        val overwrite = ProviderSetting.OpenAI(
            name = "Override"
        ).apply {
            apiKey = overwriteKey
            baseUrl = "https://override.example/v1"
        }
        val model = Model(
            modelId = "gpt-image-2",
            displayName = "gpt-image-2",
            type = ModelType.IMAGE,
            providerOverwrite = overwrite,
        )
        val provider = ProviderSetting.OpenAI(
            name = "OpenAI",
            models = listOf(model),
        ).apply { apiKey = baseKey }
        val providers = listOf(provider)

        val resolved = model.findProvider(providers)

        assertNotNull(resolved)
        assertEquals(overwrite.id, resolved?.id)
        assertEquals("Override", resolved?.name)
        assertEquals("https://override.example/v1", (resolved as ProviderSetting.OpenAI).baseUrl)
        assertEquals(overwriteKey, resolved.apiKey)
        assertNotEquals(provider.id, resolved.id)
        assertNull(providers.find { it.id == resolved.id })
    }
}
