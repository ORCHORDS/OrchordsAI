package com.orchords.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [resolveRouteCapabilities] on each [ProviderSetting] subtype.
 * Pure unit tests; no HTTP, no reflection.
 *
 * See issue #355.
 */
class RouteCapabilitiesTest {
    private fun model(modelId: String) = Model(modelId = modelId)

    @Test
    fun `openai chat model on official openai host supports both by default`() {
        val caps = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1")
            .resolveRouteCapabilities(model("gpt-4o"))
        assertEquals(RouteCapabilities(supportsTemperature = true, supportsTopP = true), caps)
    }

    @Test
    fun `openai o1-preview is denied both under the default denylist fallback`() {
        val caps = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1")
            .resolveRouteCapabilities(model("o1-preview"))
        assertEquals(RouteCapabilities(supportsTemperature = false, supportsTopP = false), caps)
    }

    @Test
    fun `openai gpt-5 is denied both under the default denylist fallback`() {
        val caps = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1")
            .resolveRouteCapabilities(model("gpt-5"))
        assertEquals(RouteCapabilities(supportsTemperature = false, supportsTopP = false), caps)
    }

    @Test
    fun `openai supportsTemperature override beats model denylist`() {
        val caps = ProviderSetting.OpenAI(
            baseUrl = "https://api.openai.com/v1",
            supportsTemperature = true,
        ).resolveRouteCapabilities(model("o1-preview"))
        assertEquals(RouteCapabilities(supportsTemperature = true, supportsTopP = false), caps)
    }

    @Test
    fun `openai kimi-k2-5 on moonshot host is denied both`() {
        val caps = ProviderSetting.OpenAI(baseUrl = "https://api.moonshot.cn/v1")
            .resolveRouteCapabilities(model("kimi-k2-5"))
        assertEquals(RouteCapabilities(supportsTemperature = false, supportsTopP = false), caps)
    }

    @Test
    fun `claude sonnet on anthropic host supports both by default`() {
        val caps = ProviderSetting.Claude(baseUrl = "https://api.anthropic.com/v1")
            .resolveRouteCapabilities(model("claude-sonnet-4-5"))
        assertEquals(RouteCapabilities(supportsTemperature = true, supportsTopP = true), caps)
    }

    @Test
    fun `claude supportsTemperature false strips temperature only`() {
        val caps = ProviderSetting.Claude(
            baseUrl = "https://api.anthropic.com/v1",
            supportsTemperature = false,
        ).resolveRouteCapabilities(model("claude-sonnet-4-5"))
        assertEquals(RouteCapabilities(supportsTemperature = false, supportsTopP = true), caps)
    }

    @Test
    fun `google gemini supports both by default`() {
        val caps = ProviderSetting.Google()
            .resolveRouteCapabilities(model("gemini-2-5-pro"))
        assertEquals(RouteCapabilities(supportsTemperature = true, supportsTopP = true), caps)
    }

    @Test
    fun `google supportsTemperature true forces temperature on`() {
        val caps = ProviderSetting.Google(supportsTemperature = true)
            .resolveRouteCapabilities(model("gemini-2-5-pro"))
        assertEquals(RouteCapabilities(supportsTemperature = true, supportsTopP = true), caps)
    }
}
