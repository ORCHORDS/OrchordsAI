package com.orchords.ai.provider.providers.google

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.ai.core.InputSchema
import com.orchords.ai.core.MessageRole
import com.orchords.ai.core.Tool
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that [GoogleProvider.buildCompletionRequestBody] applies the
 * Gemini hard cap (512) and the user-configurable cap from
 * [ProviderSetting.Google.maxToolsPerRequest]. Also confirms the
 * continuation invariant lifts a mid-flight tool to the front of the
 * emitted `functionDeclarations` (issue #359).
 */
class GoogleToolBudgetRequestTest {

    private fun tool(name: String) = Tool(
        name = name,
        description = "desc $name",
        parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
        execute = { emptyList() },
    )

    private fun provider(): GoogleProvider {
        // OkHttpClient is required; Context defaults to null for tests that
        // never issue an HTTP request.
        return GoogleProvider(OkHttpClient())
    }

    private fun call(
        provider: GoogleProvider,
        tools: List<Tool>,
        providerSetting: ProviderSetting.Google,
        messages: List<UIMessage> = listOf(UIMessage.user("hi")),
    ): JsonObject {
        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildCompletionRequestBody",
            ProviderSetting.Google::class.java,
            List::class.java,
            TextGenerationParams::class.java,
        )
        method.isAccessible = true
        val model = Model(
            modelId = "gemini-2.5-pro",
            abilities = listOf(ModelAbility.TOOL),
        )
        val params = TextGenerationParams(model = model, tools = tools)
        return method.invoke(provider, providerSetting, messages, params) as JsonObject
    }

    @Test
    fun `600 tools with no user cap is clipped to 512`() {
        val body = call(
            provider = provider(),
            tools = (0 until 600).map { tool("tool_$it") },
            providerSetting = ProviderSetting.Google(),
        )
        val functionDecls = body["tools"]!!
            .jsonArray[0]
            .jsonObject["functionDeclarations"]!!
            .jsonArray
        assertEquals(512, functionDecls.size)
    }

    @Test
    fun `user cap of 10 with 600 tools emits 10 declarations`() {
        val body = call(
            provider = provider(),
            tools = (0 until 600).map { tool("tool_$it") },
            providerSetting = ProviderSetting.Google(maxToolsPerRequest = 10),
        )
        val functionDecls = body["tools"]!!
            .jsonArray[0]
            .jsonObject["functionDeclarations"]!!
            .jsonArray
        assertEquals(10, functionDecls.size)
    }

    @Test
    fun `in-flight tool is lifted to front under user cap`() {
        // 100 tools, cap 5. Mid-flight tool is index 73 ("lookup").
        val tools = (0 until 100).map { tool("tool_$it") }
        val messages = listOf(
            UIMessage.user("hi"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "call_lookup",
                        toolName = "tool_73",
                        input = "{}",
                        output = emptyList(),
                    )
                ),
            ),
        )
        val body = call(
            provider = provider(),
            tools = tools,
            providerSetting = ProviderSetting.Google(maxToolsPerRequest = 5),
            messages = messages,
        )
        val functionDecls = body["tools"]!!
            .jsonArray[0]
            .jsonObject["functionDeclarations"]!!
            .jsonArray
        assertEquals(5, functionDecls.size)
        // The mid-flight tool lifts to the front of the budgeted list.
        // After lift: [tool_73, tool_0, tool_1, tool_2, tool_3].
        assertEquals("tool_73", functionDecls[0].jsonObject["name"]!!.jsonPrimitive.content)
    }
}
