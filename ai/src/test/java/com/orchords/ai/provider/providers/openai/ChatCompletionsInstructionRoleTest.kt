package com.orchords.ai.provider.providers.openai

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.Modality
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.util.KeyRoulette
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for #351: SYSTEM messages must serialize as `role: "developer"` for
 * OpenAI reasoning families (newer o1, o3/o4/o5, GPT-5 reasoning) and as
 * `role: "system"` for everything else, including the legacy o1-preview and
 * o1-mini models that explicitly reject the developer role.
 */
class ChatCompletionsInstructionRoleTest {
    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    @Test
    fun `bare o1 emits developer role for system instructions`() {
        assertEquals("developer", firstSystemRole(modelId = "o1", systemMessage = "You are helpful"))
    }

    @Test
    fun `date-stamped modern o1 emits developer role for system instructions`() {
        assertEquals("developer", firstSystemRole(modelId = "o1-2024-12-17", systemMessage = "Be terse"))
    }

    @Test
    fun `o3-mini emits developer role for system instructions`() {
        assertEquals("developer", firstSystemRole(modelId = "o3-mini", systemMessage = "Cite sources"))
    }

    @Test
    fun `o4-mini emits developer role for system instructions`() {
        assertEquals("developer", firstSystemRole(modelId = "o4-mini", systemMessage = "Be precise"))
    }

    @Test
    fun `gpt-5 emits developer role for system instructions`() {
        assertEquals("developer", firstSystemRole(modelId = "gpt-5", systemMessage = "Be terse"))
    }

    @Test
    fun `gpt-5-1 emits developer role for system instructions`() {
        assertEquals("developer", firstSystemRole(modelId = "gpt-5-1", systemMessage = "Be terse"))
    }

    @Test
    fun `o1-preview keeps legacy system role because it rejects developer`() {
        assertEquals("system", firstSystemRole(modelId = "o1-preview", systemMessage = "You are helpful"))
    }

    @Test
    fun `o1-mini keeps legacy system role because it rejects developer`() {
        assertEquals("system", firstSystemRole(modelId = "o1-mini", systemMessage = "You are helpful"))
    }

    @Test
    fun `gpt-5-chat keeps legacy system role`() {
        assertEquals("system", firstSystemRole(modelId = "gpt-5-chat", systemMessage = "You are helpful"))
    }

    @Test
    fun `gpt-4o keeps legacy system role`() {
        assertEquals("system", firstSystemRole(modelId = "gpt-4o", systemMessage = "You are helpful"))
    }

    @Test
    fun `claude opus keeps legacy system role regardless of routing`() {
        assertEquals("system", firstSystemRole(modelId = "claude-opus-4-7", systemMessage = "You are helpful"))
    }

    @Test
    fun `unknown model id keeps legacy system role as safe default for BYO endpoints`() {
        assertEquals(
            "system",
            firstSystemRole(
                modelId = "totally-byo-endpoint/legacy-model",
                systemMessage = "You are helpful",
            ),
        )
    }

    @Test
    fun `user messages are not affected by developer-role routing`() {
        val messages = buildMessagesJson(
            modelId = "gpt-5",
            messages = listOf(
                UIMessage.system("sys"),
                UIMessage.user("hi"),
            ),
        )
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
    }

    private fun firstSystemRole(modelId: String, systemMessage: String): String {
        val messages = buildMessagesJson(
            modelId = modelId,
            messages = listOf(
                UIMessage.system(systemMessage),
                UIMessage.user("hi"),
            ),
        )
        return messages[0].jsonObject["role"]?.jsonPrimitive?.content
            ?: error("first message is missing a role")
    }

    private fun buildMessagesJson(modelId: String, messages: List<UIMessage>): JsonArray {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildMessages",
            List::class.java,
            Model::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            List::class.java,
        )
        method.isAccessible = true
        val model = Model(
            modelId = modelId,
            abilities = emptyList(),
        )
        return method.invoke(
            api,
            messages,
            model,
            true,
            false,
            listOf(Modality.TEXT, Modality.IMAGE),
        ) as JsonArray
    }
}
