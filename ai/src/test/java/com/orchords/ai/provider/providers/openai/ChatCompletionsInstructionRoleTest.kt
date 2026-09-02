package com.orchords.ai.provider.providers.openai

import com.orchords.ai.core.MessageRole
import com.orchords.ai.provider.InstructionRoleMode
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.Modality
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.util.KeyRoulette
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for #351: SYSTEM messages must serialize as `role: "developer"` for
 * native OpenAI reasoning families that require it while custom compatible
 * routes remain conservative unless the provider explicitly overrides the
 * instruction-role capability.
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

    @Test
    fun `native openai gpt-5 request emits developer`() {
        assertEquals(
            "developer",
            firstRequestSystemRole(
                modelId = "gpt-5",
                baseUrl = "https://api.openai.com/v1",
            ),
        )
    }

    @Test
    fun `custom endpoint auto conservatively keeps system for gpt-5`() {
        assertEquals(
            "system",
            firstRequestSystemRole(
                modelId = "gpt-5",
                baseUrl = "https://example-compatible.invalid/v1",
            ),
        )
    }

    @Test
    fun `custom endpoint can explicitly opt into developer`() {
        assertEquals(
            "developer",
            firstRequestSystemRole(
                modelId = "custom-reasoning-model",
                baseUrl = "https://example-compatible.invalid/v1",
                mode = InstructionRoleMode.DEVELOPER,
            ),
        )
    }

    @Test
    fun `custom endpoint can explicitly opt out to system`() {
        assertEquals(
            "system",
            firstRequestSystemRole(
                modelId = "gpt-5",
                baseUrl = "https://example-compatible.invalid/v1",
                mode = InstructionRoleMode.SYSTEM,
            ),
        )
    }

    @Test
    fun `native endpoint explicit system override wins over model default`() {
        assertEquals(
            "system",
            firstRequestSystemRole(
                modelId = "gpt-5",
                baseUrl = "https://api.openai.com/v1",
                mode = InstructionRoleMode.SYSTEM,
            ),
        )
    }

    @Test
    fun `switching model recomputes wire instruction role`() {
        val providerUrl = "https://api.openai.com/v1"
        assertEquals("developer", firstRequestSystemRole("gpt-5", providerUrl))
        assertEquals("system", firstRequestSystemRole("gpt-4o", providerUrl))
    }

    @Test
    fun `switching route recomputes wire instruction role`() {
        assertEquals(
            "developer",
            firstRequestSystemRole("gpt-5", "https://api.openai.com/v1"),
        )
        assertEquals(
            "system",
            firstRequestSystemRole("gpt-5", "https://example-compatible.invalid/v1"),
        )
        assertEquals(
            "developer",
            firstRequestSystemRole(
                modelId = "gpt-5",
                baseUrl = "https://example-compatible.invalid/v1",
                mode = InstructionRoleMode.DEVELOPER,
            ),
        )
    }

    @Test
    fun `request serialization does not mutate canonical system role`() {
        val system = UIMessage.system("canonical policy")
        val beforeRole = system.role
        val body = buildRequest(
            modelId = "gpt-5",
            baseUrl = "https://api.openai.com/v1",
            messages = listOf(system, UIMessage.user("hi")),
        )

        assertEquals("developer", body["messages"]?.jsonArray?.get(0)?.jsonObject?.get("role")?.jsonPrimitive?.content)
        assertEquals(MessageRole.SYSTEM, beforeRole)
        assertEquals(MessageRole.SYSTEM, system.role)
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

    private fun firstRequestSystemRole(
        modelId: String,
        baseUrl: String,
        mode: InstructionRoleMode = InstructionRoleMode.AUTO,
    ): String {
        val body = buildRequest(
            modelId = modelId,
            baseUrl = baseUrl,
            mode = mode,
        )
        return body["messages"]?.jsonArray?.get(0)?.jsonObject?.get("role")?.jsonPrimitive?.content
            ?: error("first request message is missing a role")
    }

    private fun buildRequest(
        modelId: String,
        baseUrl: String,
        mode: InstructionRoleMode = InstructionRoleMode.AUTO,
        messages: List<UIMessage> = listOf(UIMessage.system("sys"), UIMessage.user("hi")),
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
            modelId = modelId,
            abilities = emptyList(),
        )
        val params = TextGenerationParams(
            model = model,
            maxTokens = null,
        )
        val providerSetting = ProviderSetting.OpenAI(
            baseUrl = baseUrl,
            instructionRoleMode = mode,
        )
        return method.invoke(
            api,
            messages,
            params,
            providerSetting,
            false,
        ) as JsonObject
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
