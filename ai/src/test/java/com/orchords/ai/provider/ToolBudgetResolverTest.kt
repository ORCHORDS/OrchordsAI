package com.orchords.ai.provider

import com.orchords.ai.core.InputSchema
import com.orchords.ai.core.MessageRole
import com.orchords.ai.core.Tool
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for [resolveToolBudget].
 *
 * The resolver is the single source of truth for the tool-count budget
 * applied at every provider wire-build site (issue #359). It enforces:
 *
 *  1. Hard vendor cap (Gemini 512, others none).
 *  2. User-configurable cap from [ProviderSetting.maxToolsPerRequest].
 *  3. Continuation invariant: tools whose call is mid-flight in the
 *     conversation (present as [UIMessagePart.Tool] parts) are lifted to
 *     the front of the budgeted list before any truncation.
 *
 * See [GEMINI_HARD_TOOL_CAP].
 */
class ToolBudgetResolverTest {

    private fun tool(name: String) = Tool(
        name = name,
        description = "desc $name",
        parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
        execute = { emptyList() },
    )

    private fun inFlightTool(callId: String, name: String) = UIMessagePart.Tool(
        toolCallId = callId,
        toolName = name,
        input = "{}",
        output = emptyList(),
    )

    @Test
    fun `empty tools returns empty list`() {
        val result = resolveToolBudget(
            tools = emptyList(),
            messages = listOf(UIMessage.user("hi")),
            providerSetting = ProviderSetting.Google(),
        )
        assertEquals(emptyList<Tool>(), result)
    }

    @Test
    fun `tools count under cap are returned as-is in order`() {
        val tools = (0 until 100).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.Google(maxToolsPerRequest = 100),
        )
        assertEquals(tools, result)
    }

    @Test
    fun `Google applies hard cap of 512 with no user cap`() {
        val tools = (0 until 600).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.Google(),
        )
        assertEquals(512, result.size)
    }

    @Test
    fun `Google user cap tighter than hard cap wins`() {
        val tools = (0 until 600).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.Google(maxToolsPerRequest = 10),
        )
        assertEquals(10, result.size)
        // First 10 in input order
        assertEquals((0 until 10).map { "tool_$it" }, result.map { it.name })
    }

    @Test
    fun `OpenAI has no vendor cap and no user cap returns all tools`() {
        val tools = (0 until 100).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.OpenAI(),
        )
        assertEquals(100, result.size)
    }

    @Test
    fun `OpenAI user cap is enforced`() {
        val tools = (0 until 100).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.OpenAI(maxToolsPerRequest = 50),
        )
        assertEquals(50, result.size)
    }

    @Test
    fun `Claude has no vendor cap and no user cap returns all tools`() {
        val tools = (0 until 200).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.Claude(),
        )
        assertEquals(200, result.size)
    }

    @Test
    fun `Claude user cap is enforced`() {
        val tools = (0 until 200).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.Claude(maxToolsPerRequest = 25),
        )
        assertEquals(25, result.size)
    }

    @Test
    fun `null providerSetting returns tools as-is`() {
        val tools = (0 until 50).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = null,
        )
        assertEquals(50, result.size)
    }

    @Test
    fun `duplicates by name are collapsed`() {
        val tools = listOf(
            tool("alpha"),
            tool("beta"),
            tool("alpha"), // duplicate
            tool("gamma"),
        )
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.OpenAI(),
        )
        assertEquals(listOf("alpha", "beta", "gamma"), result.map { it.name })
    }

    @Test
    fun `in-flight tool is lifted to front when cap would otherwise drop it`() {
        // 5 tools, cap at 2 — by input order the first two would survive.
        // But "tool_4" is mid-flight, so it must be lifted to position 0,
        // and "tool_0" drops off.
        val tools = (0 until 5).map { tool("tool_$it") }
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(inFlightTool("call_99", "tool_4")),
            ),
        )
        val result = resolveToolBudget(
            tools = tools,
            messages = messages,
            providerSetting = ProviderSetting.OpenAI(maxToolsPerRequest = 2),
        )
        assertEquals(2, result.size)
        assertEquals("tool_4", result[0].name)
        assertEquals("tool_0", result[1].name)
    }

    @Test
    fun `multiple in-flight tools all lifted before non-in-flight`() {
        // Cap at 3; three of the five tools are mid-flight.
        val tools = (0 until 5).map { tool("tool_$it") }
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    inFlightTool("c1", "tool_2"),
                    inFlightTool("c2", "tool_4"),
                    inFlightTool("c3", "tool_0"),
                ),
            ),
        )
        val result = resolveToolBudget(
            tools = tools,
            messages = messages,
            providerSetting = ProviderSetting.OpenAI(maxToolsPerRequest = 3),
        )
        assertEquals(3, result.size)
        // In-flight tools are lifted in input-list order: tool_0, tool_2, tool_4.
        // Non-in-flight tools (tool_1, tool_3) have no leftover slots.
        assertEquals(listOf("tool_0", "tool_2", "tool_4"), result.map { it.name })
    }

    @Test
    fun `in-flight tool already within cap stays in place`() {
        val tools = (0 until 5).map { tool("tool_$it") }
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(inFlightTool("call_99", "tool_1")),
            ),
        )
        val result = resolveToolBudget(
            tools = tools,
            messages = messages,
            providerSetting = ProviderSetting.OpenAI(maxToolsPerRequest = 5),
        )
        // No truncation happens, but the in-flight prefix lifts tool_1 to front.
        // Final order: tool_1, tool_0, tool_2, tool_3, tool_4.
        assertEquals(
            listOf("tool_1", "tool_0", "tool_2", "tool_3", "tool_4"),
            result.map { it.name },
        )
    }

    @Test
    fun `in-flight tool referencing a name not in the tool list is a no-op`() {
        val tools = (0 until 3).map { tool("tool_$it") }
        val messages = listOf(
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(inFlightTool("call_99", "ghost_tool")),
            ),
        )
        val result = resolveToolBudget(
            tools = tools,
            messages = messages,
            providerSetting = ProviderSetting.OpenAI(maxToolsPerRequest = 2),
        )
        assertEquals(listOf("tool_0", "tool_1"), result.map { it.name })
    }

    @Test
    fun `under cap and no in-flight returns tools in original order`() {
        val tools = (0 until 5).map { tool("tool_$it") }
        val result = resolveToolBudget(
            tools = tools,
            messages = emptyList(),
            providerSetting = ProviderSetting.OpenAI(maxToolsPerRequest = 10),
        )
        assertEquals(tools.map { it.name }, result.map { it.name })
    }
}
