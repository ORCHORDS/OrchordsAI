package com.orchords.ai.provider.providers.openai

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.StreamingUsageMode
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.util.KeyRoulette
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Request-shape fixtures for the `stream_options.include_usage` capability
 * gate (#352): the field is emitted only when the resolved capability says
 * the route supports it, and never on non-streaming requests.
 */
class ChatCompletionsStreamingUsageTest {
    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `native openai streaming request includes stream_options`() {
        val body = buildRequest(baseUrl = "https://api.openai.com/v1")

        assertEquals(
            true,
            body["stream_options"]?.jsonObject?.get("include_usage")?.jsonPrimitive?.booleanOrNull
        )
    }

    @Test
    fun `strict unknown endpoint omits stream_options by default`() {
        val body = buildRequest(baseUrl = "https://adb-123.databricks.azure.com/serving-endpoints")

        assertNull(body["stream_options"])
    }

    @Test
    fun `mistral strict endpoint omits stream_options`() {
        val body = buildRequest(baseUrl = "https://api.mistral.ai/v1")

        assertNull(body["stream_options"])
    }

    @Test
    fun `undocumented compatible host omits stream_options`() {
        // Zhipu does not document the field; conservative default must omit it.
        val body = buildRequest(baseUrl = "https://open.bigmodel.cn/api/paas/v4")

        assertNull(body["stream_options"])
    }

    @Test
    fun `documented compatible hosts include stream_options`() {
        listOf(
            "https://api.deepseek.com/v1",
            "https://api.moonshot.cn/v1",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "https://api.siliconflow.cn/v1",
            "https://api.hunyuan.cloud.tencent.com/v1",
            "https://openrouter.ai/api/v1",
            "https://api.x.ai/v1",
        ).forEach { baseUrl ->
            val body = buildRequest(baseUrl = baseUrl)
            assertEquals(
                "expected stream_options on $baseUrl",
                true,
                body["stream_options"]?.jsonObject?.get("include_usage")?.jsonPrimitive?.booleanOrNull
            )
        }
    }

    @Test
    fun `explicit enabled override emits the field on an unknown host`() {
        val body = buildRequest(
            baseUrl = "https://ai-gateway.vercel.sh/v1",
            mode = StreamingUsageMode.ENABLED,
        )

        assertEquals(
            true,
            body["stream_options"]?.jsonObject?.get("include_usage")?.jsonPrimitive?.booleanOrNull
        )
    }

    @Test
    fun `explicit disabled override omits the field on a documented host`() {
        val body = buildRequest(
            baseUrl = "https://api.openai.com/v1",
            mode = StreamingUsageMode.DISABLED,
        )

        assertNull(body["stream_options"])
    }

    @Test
    fun `non streaming request never contains stream_options`() {
        val body = buildRequest(baseUrl = "https://api.openai.com/v1", stream = false)

        assertNull(body["stream_options"])
    }

    private fun buildRequest(
        baseUrl: String,
        mode: StreamingUsageMode = StreamingUsageMode.AUTO,
        stream: Boolean = true,
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
            modelId = "gpt-4o",
            abilities = emptyList(),
        )
        val params = TextGenerationParams(
            model = model,
            maxTokens = null,
        )
        val providerSetting = ProviderSetting.OpenAI(
            baseUrl = baseUrl,
            streamingUsageMode = mode,
        )
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            stream,
        ) as JsonObject
    }
}
