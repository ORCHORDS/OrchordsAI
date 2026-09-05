package com.orchords.ai.provider.providers.openai

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
import com.orchords.ai.util.KeyRoulette
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies that [ChatCompletionsAPI.buildChatCompletionRequest] routes
 * `params.tools` through [com.orchords.ai.provider.resolveToolBudget] so
 * user-configurable caps from `ProviderSetting.OpenAI.maxToolsPerRequest`
 * and the continuation invariant are honored on the wire (issue #359).
 */
class ChatCompletionsToolBudgetRequestTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `default ProviderSetting sends every tool`() {
        val body = buildRequest(
            tools = (0 until 200).map { testTool("tool_$it") },
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(200, toolEntries.size)
    }

    @Test
    fun `maxToolsPerRequest of 10 caps emitted tools at 10`() {
        val body = buildRequest(
            tools = (0 until 200).map { testTool("tool_$it") },
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.openai.com/v1",
                maxToolsPerRequest = 10,
            ),
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(10, toolEntries.size)
    }

    @Test
    fun `in-flight tool is preserved and lifted to front under cap`() {
        // 200 tools, one mid-flight named "browse" (index 50). Cap 10.
        // Expected: browse lifts to front, then the first 9 non-in-flight
        // tools by input order fill the remaining slots.
        val tools = (0 until 200).map { testTool("tool_$it") }
        val messages = listOf(
            UIMessage.user("hi"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "call_browse",
                        toolName = "tool_50",
                        input = "{}",
                        output = emptyList(),
                    )
                ),
            ),
        )
        val body = buildRequest(
            tools = tools,
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.openai.com/v1",
                maxToolsPerRequest = 10,
            ),
            messagesOverride = messages,
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(10, toolEntries.size)
        val firstName = toolEntries[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content
        assertEquals("tool_50", firstName)
    }

    private fun buildRequest(
        tools: List<Tool>,
        providerSetting: ProviderSetting.OpenAI,
        messagesOverride: List<UIMessage>? = null,
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
            abilities = listOf(ModelAbility.TOOL),
        )
        val params = TextGenerationParams(
            model = model,
            tools = tools,
        )
        return method.invoke(
            api,
            messagesOverride ?: listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true,
        ) as JsonObject
    }

    private fun testTool(name: String) = Tool(
        name = name,
        description = "desc $name",
        parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
        execute = { emptyList() },
    )
}
