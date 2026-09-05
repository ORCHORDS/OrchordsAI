package com.orchords.ai.provider.providers.claude

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
import org.junit.Before
import org.junit.Test

/**
 * Verifies that [ClaudeProvider.buildMessageRequest] routes
 * `params.tools` through [com.orchords.ai.provider.resolveToolBudget] so
 * the user cap from [ProviderSetting.Claude.maxToolsPerRequest] and the
 * continuation invariant are honored on the Anthropic Messages wire
 * (issue #359).
 */
class ClaudeToolBudgetRequestTest {

    private lateinit var provider: ClaudeProvider

    @Before
    fun setUp() {
        provider = ClaudeProvider(OkHttpClient())
    }

    private fun testTool(name: String) = Tool(
        name = name,
        description = "desc $name",
        parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
        execute = { emptyList() },
    )

    private fun toolParams(tools: List<Tool>): TextGenerationParams = TextGenerationParams(
        model = Model(modelId = "claude-3-7-sonnet", abilities = listOf(ModelAbility.TOOL)),
        tools = tools,
    )

    private fun buildRequest(
        providerSetting: ProviderSetting.Claude,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): JsonObject {
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            ProviderSetting.Claude::class.java,
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType!!,
        )
        method.isAccessible = true
        return method.invoke(provider, providerSetting, messages, params, false) as JsonObject
    }

    @Test
    fun `default ProviderSetting sends every tool`() {
        val tools = (0 until 200).map { testTool("tool_$it") }
        val body = buildRequest(
            providerSetting = ProviderSetting.Claude(),
            messages = listOf(UIMessage.user("hi")),
            params = toolParams(tools),
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(200, toolEntries.size)
    }

    @Test
    fun `maxToolsPerRequest of 10 caps emitted tools at 10`() {
        val tools = (0 until 200).map { testTool("tool_$it") }
        val body = buildRequest(
            providerSetting = ProviderSetting.Claude(maxToolsPerRequest = 10),
            messages = listOf(UIMessage.user("hi")),
            params = toolParams(tools),
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(10, toolEntries.size)
    }

    @Test
    fun `in-flight tool is preserved and lifted to front under cap`() {
        val tools = (0 until 200).map { testTool("tool_$it") }
        val messages = listOf(
            UIMessage.user("hi"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "call_lookup",
                        toolName = "tool_50",
                        input = "{}",
                        output = emptyList(),
                    )
                ),
            ),
        )
        val body = buildRequest(
            providerSetting = ProviderSetting.Claude(maxToolsPerRequest = 10),
            messages = messages,
            params = toolParams(tools),
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(10, toolEntries.size)
        val firstName = toolEntries[0].jsonObject["name"]!!.jsonPrimitive.content
        assertEquals("tool_50", firstName)
    }
}
