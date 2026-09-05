package com.orchords.ai.provider

import com.orchords.ai.core.InputSchema
import com.orchords.ai.core.MessageRole
import com.orchords.ai.core.Tool
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolBudgetContinuationTest {
    private fun tool(name: String) = Tool(name = name, description = name,
        parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) }, execute = { emptyList() })
    private fun call(name: String, completed: Boolean) = UIMessagePart.Tool(
        toolCallId = "call-$name", toolName = name, input = "{}",
        output = if (completed) listOf(UIMessagePart.Text("done")) else emptyList(),
    )
    @Test fun `completed history cannot crowd out an active continuation`() {
        val tools = listOf(tool("old"), tool("new"))
        val messages = listOf(UIMessage(role = MessageRole.ASSISTANT,
            parts = listOf(call("old", true), call("new", false))))
        assertEquals(listOf("new"), resolveToolBudget(tools, messages,
            ProviderSetting.OpenAI(maxToolsPerRequest = 1)).map { it.name })
    }
    @Test(expected = ToolBudgetExceededException::class)
    fun `required overflow fails locally instead of truncating a pending tool`() {
        resolveToolBudget(listOf(tool("a"), tool("b")),
            listOf(UIMessage(role = MessageRole.ASSISTANT, parts = listOf(call("a", false), call("b", false)))),
            ProviderSetting.Google(maxToolsPerRequest = 1))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `negative imported cap fails even with an empty registry`() {
        resolveToolBudget(emptyList(), emptyList(), ProviderSetting.OpenAI(maxToolsPerRequest = -1))
    }
    @Test fun `zero cap without pending calls disables declarations`() {
        assertEquals(emptyList<Tool>(), resolveToolBudget(listOf(tool("a")), emptyList(),
            ProviderSetting.Claude(maxToolsPerRequest = 0)))
    }
    @Test fun `hard cap cannot be relaxed by user budget`() {
        assertEquals(512, selectBudgetedTools((0 until 600).toList(), { it.toString() }, emptySet(), 512, 700).size)
    }
}
