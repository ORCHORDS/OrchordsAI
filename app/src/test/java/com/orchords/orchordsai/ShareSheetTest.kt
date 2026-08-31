package com.orchords.orchordsai

import com.orchords.ai.provider.BalanceOption
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.testsupport.CLAUDE_KEY
import com.orchords.orchordsai.testsupport.GOOGLE_KEY
import com.orchords.orchordsai.testsupport.OPENAI_KEY
import com.orchords.orchordsai.testsupport.PROXY_KEY
import com.orchords.orchordsai.testsupport.SHARED_KEY
import com.orchords.orchordsai.testsupport.claudeWith
import com.orchords.orchordsai.testsupport.googleWith
import com.orchords.orchordsai.testsupport.openAiWith
import com.orchords.orchordsai.ui.components.ui.decodeProviderSetting
import com.orchords.orchordsai.ui.components.ui.encodeForShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ShareSheetTest {
    @Test
    fun `share round trip should restore OpenAI settings without models`() {
        val originalId = Uuid.random()
        val key = OPENAI_KEY
        val original = ProviderSetting.OpenAI(
            id = originalId,
            enabled = true,
            name = "Test OpenAI",
            models = listOf(
                Model(
                    id = Uuid.random(),
                    displayName = "gpt-4",
                )
            ),
            chatCompletionsPath = "/chat/completions",
            useResponseApi = false,
            balanceOption = BalanceOption(enabled = false)
        ).apply {
            apiKey = key
            baseUrl = "https://api.openai.com/v1"
        }

        val encoded = original.encodeForShare()
        val decoded = decodeProviderSetting(encoded)

        assertTrue(decoded is ProviderSetting.OpenAI)
        val decodedOpenAI = decoded as ProviderSetting.OpenAI
        assertEquals(originalId, decodedOpenAI.id)
        assertEquals("Test OpenAI", decodedOpenAI.name)
        assertEquals(key, decodedOpenAI.apiKey)
        assertEquals("https://api.openai.com/v1", decodedOpenAI.baseUrl)
        assertTrue(decodedOpenAI.models.isEmpty())
    }

    @Test
    fun `decode should restore Google provider correctly`() {
        val originalId = Uuid.random()
        val key = GOOGLE_KEY
        val original = ProviderSetting.Google(
            id = originalId,
            enabled = true,
            name = "Test Google",
            models = emptyList(),
            vertexAI = false
        ).apply {
            apiKey = key
            baseUrl = "https://generativelanguage.googleapis.com/v1beta"
        }

        val encoded = original.encodeForShare()
        val decoded = decodeProviderSetting(encoded)

        assertTrue(decoded is ProviderSetting.Google)
        val decodedGoogle = decoded as ProviderSetting.Google
        assertEquals(originalId, decodedGoogle.id)
        assertEquals("Test Google", decodedGoogle.name)
        assertEquals(key, decodedGoogle.apiKey)
        assertEquals(false, decodedGoogle.vertexAI)
    }

    @Test
    fun `decode should restore Claude provider correctly`() {
        val originalId = Uuid.random()
        val key = CLAUDE_KEY
        val original = ProviderSetting.Claude(
            id = originalId,
            enabled = false,
            name = "Test Claude",
            models = emptyList()
        ).apply {
            apiKey = key
            baseUrl = "https://api.anthropic.com/v1"
        }

        val encoded = original.encodeForShare()
        val decoded = decodeProviderSetting(encoded)

        assertTrue(decoded is ProviderSetting.Claude)
        val decodedClaude = decoded as ProviderSetting.Claude
        assertEquals(originalId, decodedClaude.id)
        assertEquals("Test Claude", decodedClaude.name)
        assertEquals(key, decodedClaude.apiKey)
        assertEquals(false, decodedClaude.enabled)
    }

    @Test
    fun `decode should handle balance option`() {
        val original = openAiWith(
            name = "Test with Balance",
            key = PROXY_KEY,
            baseUrl = "https://api.test.com",
            balanceOption = BalanceOption(
                enabled = true,
                apiPath = "/custom/credits",
                resultPath = "data.balance"
            )
        )

        val encoded = original.encodeForShare()
        val decoded = decodeProviderSetting(encoded) as ProviderSetting.OpenAI

        assertEquals(true, decoded.balanceOption.enabled)
        assertEquals("/custom/credits", decoded.balanceOption.apiPath)
        assertEquals("data.balance", decoded.balanceOption.resultPath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode should throw exception for invalid prefix`() {
        decodeProviderSetting("invalid-string")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode should throw exception for wrong version`() {
        decodeProviderSetting("ai-provider:v2:somedata")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode should throw exception for invalid base64`() {
        decodeProviderSetting("ai-provider:v1:not-valid-base64!!!")
    }

    @Test
    fun `encode and decode should be reversible`() {
        val providers = listOf(
            openAiWith(name = "OpenAI Test", key = SHARED_KEY + "-1", baseUrl = "url1"),
            googleWith(name = "Google Test", key = SHARED_KEY + "-2", baseUrl = "url2")
                .also { it.vertexAI = true; it.projectId = "project-123" },
            claudeWith(name = "Claude Test", key = SHARED_KEY + "-3", baseUrl = "url3"),
        )

        providers.forEach { original ->
            val encoded = original.encodeForShare()
            val decoded = decodeProviderSetting(encoded)

            assertEquals(original.id, decoded.id)
            assertEquals(original.name, decoded.name)
            assertEquals(original.enabled, decoded.enabled)
        }
    }
}
