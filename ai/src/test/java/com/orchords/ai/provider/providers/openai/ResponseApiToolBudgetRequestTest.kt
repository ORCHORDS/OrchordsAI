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
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies that [ResponseAPI.buildRequestBody] routes `params.tools`
 * through [com.orchords.ai.provider.resolveToolBudget] so the user cap
 * from [ProviderSetting.OpenAI.maxToolsPerRequest] and the continuation
 * invariant are honored on the Responses wire (issue #359).
 */
class ResponseApiToolBudgetRequestTest {

    private lateinit var api: ResponseAPI

    @Before
    fun setUp() {
        api = ResponseAPI(OkHttpClient())
    }

    private fun testTool(name: String) = Tool(
        name = name,
        description = "desc $name",
        parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
        execute = { emptyList() },
    )

    private fun toolParams(tools: List<Tool>): TextGenerationParams = TextGenerationParams(
        model = Model(
            modelId = "gpt-4.1",
            displayName = "gpt-4.1",
            abilities = listOf(ModelAbility.TOOL),
        ),
        tools = tools,
    )

    @Test
    fun `default ProviderSetting sends every tool`() {
        val tools = (0 until 200).map { testTool("tool_$it") }
        val body = api.buildRequestBody(
            providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1"),
            messages = listOf(UIMessage.user("hi")),
            params = toolParams(tools),
            stream = false,
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(200, toolEntries.size)
    }

    @Test
    fun `maxToolsPerRequest of 10 caps emitted tools at 10`() {
        val tools = (0 until 200).map { testTool("tool_$it") }
        val body = api.buildRequestBody(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.openai.com/v1",
                maxToolsPerRequest = 10,
            ),
            messages = listOf(UIMessage.user("hi")),
            params = toolParams(tools),
            stream = false,
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
                        toolCallId = "call_browse",
                        toolName = "tool_50",
                        input = "{}",
                        output = emptyList(),
                    )
                ),
            ),
        )
        val body = api.buildRequestBody(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.openai.com/v1",
                maxToolsPerRequest = 10,
            ),
            messages = messages,
            params = toolParams(tools),
            stream = false,
        )
        val toolEntries = body["tools"]!!.jsonArray
        assertEquals(10, toolEntries.size)
        val firstName = toolEntries[0].jsonObject["name"]!!.jsonPrimitive.content
        assertEquals("tool_50", firstName)
    }
}
