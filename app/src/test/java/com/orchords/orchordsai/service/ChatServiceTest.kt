package com.orchords.orchordsai.service

import kotlinx.serialization.json.JsonPrimitive
import com.orchords.ai.core.ReasoningLevel
import com.orchords.ai.provider.BuiltInTools
import com.orchords.ai.provider.CustomBody
import com.orchords.ai.provider.CustomHeader
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.data.model.Assistant
import com.orchords.orchordsai.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatServiceTest {
    @Test
    fun `fork conversation inherits folder and workspace context`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            title = "Source conversation",
            messageNodes = emptyList(),
            workspaceCwd = "/workspace/project",
            folderId = Uuid.random(),
        )

        val fork = createForkConversation(source, emptyList())

        assertNotEquals(source.id, fork.id)
        assertEquals(source.assistantId, fork.assistantId)
        assertEquals(source.workspaceCwd, fork.workspaceCwd)
        assertEquals(source.folderId, fork.folderId)
        assertEquals("", fork.title)
        assertFalse(fork.isPinned)
    }

    @Test
    fun `background generation params include model custom request configuration`() {
        val headers = listOf(CustomHeader(name = "X-Gateway-Token", value = "test-token"))
        val bodies = listOf(CustomBody(key = "gateway_mode", value = JsonPrimitive("strict")))
        val model = Model(
            modelId = "custom-chat-model",
            customHeaders = headers,
            customBodies = bodies,
        )

        val params = backgroundTextGenerationParams(model)

        assertEquals(model, params.model)
        assertEquals(ReasoningLevel.AUTO, params.reasoningLevel)
        assertEquals(headers, params.customHeaders)
        assertEquals(bodies, params.customBody)
    }

    @Test
    fun `external web search is disabled when assistant preference is disabled`() {
        val assistant = Assistant(enableWebSearch = false)
        val model = Model()

        assertFalse(shouldUseExternalWebSearch(assistant, model, providerSetting = null))
    }

    @Test
    fun `external web search is enabled when assistant preference is enabled`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model()

        assertTrue(shouldUseExternalWebSearch(assistant, model, providerSetting = null))
    }

    @Test
    fun `built-in search suppresses enabled external web search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(
            shouldUseExternalWebSearch(
                assistant,
                model,
                providerSetting = ProviderSetting.Claude(),
            ),
        )
    }

    @Test
    fun `built-in search remains exclusive when external web search is disabled`() {
        val assistant = Assistant(enableWebSearch = false)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(
            shouldUseExternalWebSearch(
                assistant,
                model,
                providerSetting = ProviderSetting.Claude(),
            ),
        )
    }

    @Test
    fun `unrelated built-in tools do not suppress external web search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.UrlContext))

        assertTrue(shouldUseExternalWebSearch(assistant, model, providerSetting = null))
    }

    @Test
    fun `external web search fallback engages on Claude-compatible route even when model claims BuiltInTools Search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.Search))
        val provider = ProviderSetting.Claude(
            baseUrl = "https://relay.example.com/v1",
        )

        assertTrue(shouldUseExternalWebSearch(assistant, model, providerSetting = provider))
    }

    @Test
    fun `external web search stays disabled on native Anthropic route when model claims BuiltInTools Search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(
            shouldUseExternalWebSearch(
                assistant,
                model,
                providerSetting = ProviderSetting.Claude(),
            ),
        )
    }

    @Test
    fun `external web search stays disabled for non-Claude providers when model claims BuiltInTools Search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(
            shouldUseExternalWebSearch(
                assistant,
                model,
                providerSetting = ProviderSetting.OpenAI(),
            ),
        )
        assertFalse(
            shouldUseExternalWebSearch(
                assistant,
                model,
                providerSetting = ProviderSetting.Google(),
            ),
        )
    }
}
