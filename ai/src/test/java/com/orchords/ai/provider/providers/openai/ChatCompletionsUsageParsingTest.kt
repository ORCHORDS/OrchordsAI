package com.orchords.ai.provider.providers.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.orchords.ai.core.TokenUsage
import com.orchords.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatCompletionsAPI token usage parsing.
 */
class ChatCompletionsUsageParsingTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    // Helper to invoke private parseTokenUsage via reflection
    private fun parseTokenUsage(usage: JsonObject): TokenUsage? {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "parseTokenUsage",
            JsonObject::class.java
        )
        method.isAccessible = true
        return method.invoke(api, usage) as TokenUsage?
    }

    @Test
    fun `cached tokens fall back across provider dialects`() {
        fun usage(jsonStr: String) = parseTokenUsage(Json.parseToJsonElement(jsonStr).jsonObject)

        assertEquals(
            12,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"prompt_tokens_details":{"cached_tokens":12}}""")?.cachedTokens
        )
        assertEquals(
            7,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"cached_tokens":7}""")?.cachedTokens
        )
        assertEquals(
            5,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"prompt_cache_hit_tokens":5,"prompt_cache_miss_tokens":3}""")?.cachedTokens
        )
        assertEquals(
            12,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"prompt_tokens_details":{"cached_tokens":12},"cached_tokens":7}""")?.cachedTokens
        )
        assertEquals(
            0,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}""")?.cachedTokens
        )
    }
}
