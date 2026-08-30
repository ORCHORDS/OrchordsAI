package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.testsupport.GOOGLE_KEY
import me.rerere.rikkahub.testsupport.OPENAI_KEY
import me.rerere.rikkahub.testsupport.PROXY_KEY
import me.rerere.rikkahub.testsupport.claudeWith
import me.rerere.rikkahub.testsupport.googleWith
import me.rerere.rikkahub.testsupport.openAiWith
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ProviderConfigureConvertToTest {
    @Test
    fun `convertTo should keep common fields and switch official endpoint to target default`() {
        val model = Model(
            id = Uuid.random(),
            modelId = "gpt-custom",
            displayName = "GPT Custom"
        )
        val balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/custom/credits",
            resultPath = "data.balance"
        )
        val original = openAiWith(
            name = "My Provider",
            key = OPENAI_KEY,
            baseUrl = "https://api.openai.com/v1",
            enabled = false,
            models = listOf(model),
            balanceOption = balanceOption,
        )

        val converted = original.convertTo(ProviderSetting.Google::class)
        assertTrue(converted is ProviderSetting.Google)
        val google = converted as ProviderSetting.Google

        assertEquals(original.id, google.id)
        assertEquals(original.enabled, google.enabled)
        assertEquals(original.name, google.name)
        assertEquals(original.models, google.models)
        assertEquals(original.balanceOption, google.balanceOption)
        assertEquals(original.apiKey, google.apiKey)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", google.baseUrl)
    }

    @Test
    fun `convertTo should preserve third-party host and replace version suffix`() {
        val original = openAiWith(
            name = "Proxy OpenAI",
            key = PROXY_KEY,
            baseUrl = "https://gateway.example.com/api/v1",
        )

        val converted = original.convertTo(ProviderSetting.Google::class) as ProviderSetting.Google
        assertEquals("https://gateway.example.com/api/v1beta", converted.baseUrl)
        assertEquals("gateway.example.com", converted.baseUrl.toHttpUrlOrNull()?.host)
    }

    @Test
    fun `convertTo should preserve third-party host and append target path when needed`() {
        val original = googleWith(
            name = "Proxy Google",
            key = GOOGLE_KEY,
            baseUrl = "https://proxy.example.com/vendor/gemini",
        )

        val converted = original.convertTo(ProviderSetting.OpenAI::class) as ProviderSetting.OpenAI
        assertEquals("https://proxy.example.com/vendor/gemini/v1", converted.baseUrl)
        assertEquals("proxy.example.com", converted.baseUrl.toHttpUrlOrNull()?.host)
    }

    @Test
    fun `convertTo should preserve third-party host when switching to claude`() {
        val original = openAiWith(
            name = "Proxy OpenAI",
            key = PROXY_KEY,
            baseUrl = "https://gateway.example.com/proxy/v1beta",
        )

        val converted = original.convertTo(ProviderSetting.Claude::class) as ProviderSetting.Claude
        assertEquals("https://gateway.example.com/proxy/v1", converted.baseUrl)
        assertEquals("gateway.example.com", converted.baseUrl.toHttpUrlOrNull()?.host)
    }

    @Test
    fun `convertTo should return same instance for same type`() {
        val original = openAiWith(
            name = "Same Type",
            key = OPENAI_KEY,
            baseUrl = "https://api.openai.com/v1",
        )

        val converted = original.convertTo(ProviderSetting.OpenAI::class)
        assertSame(original, converted)
    }

    @Test
    fun `convertTo should keep original base url when source url is invalid`() {
        val original = claudeWith(
            name = "Invalid URL Provider",
            key = PROXY_KEY,
            baseUrl = "not-a-url",
        )

        val converted = original.convertTo(ProviderSetting.OpenAI::class) as ProviderSetting.OpenAI
        assertEquals("not-a-url", converted.baseUrl)
    }
}
