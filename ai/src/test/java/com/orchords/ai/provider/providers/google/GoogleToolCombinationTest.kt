package com.orchords.ai.provider.providers.google

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import com.orchords.ai.core.Tool
import com.orchords.ai.provider.BuiltInTools
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.provider.stream.SseEvent
import com.orchords.ai.ui.ServerToolMetadata
import com.orchords.ai.ui.ServerToolProtocol
import com.orchords.ai.ui.ServerToolStatus
import com.orchords.ai.ui.StreamChunkHandler
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.ai.ui.metadataAs
import com.orchords.ai.util.json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleToolCombinationTest {
    private val provider = GoogleProvider(OkHttpClient())

    @Test
    fun `built-in and function tools enable server-side tool invocations`() {
        val body = invokeBuildRequestBody(
            providerSetting = ProviderSetting.Google(),
            messages = listOf(UIMessage.user("search and call a tool")),
            params = TextGenerationParams(
                model = toolModel(tools = setOf(BuiltInTools.Search)),
                tools = listOf(testTool()),
            ),
        )

        val tools = body["tools"]!!.jsonArray
        assertEquals(2, tools.size)
        assertTrue(tools.any { it.jsonObject.containsKey("functionDeclarations") })
        assertTrue(tools.any { it.jsonObject.containsKey("googleSearch") })
        assertTrue(
            body["toolConfig"]!!.jsonObject["includeServerSideToolInvocations"]!!
                .jsonPrimitive.boolean
        )
    }

    @Test
    fun `single tool category does not enable server-side tool invocations`() {
        val builtInOnly = invokeBuildRequestBody(
            providerSetting = ProviderSetting.Google(),
            messages = listOf(UIMessage.user("search")),
            params = TextGenerationParams(
                model = toolModel(tools = setOf(BuiltInTools.Search)),
            ),
        )
        val functionOnly = invokeBuildRequestBody(
            providerSetting = ProviderSetting.Google(),
            messages = listOf(UIMessage.user("call a tool")),
            params = TextGenerationParams(
                model = toolModel(),
                tools = listOf(testTool()),
            ),
        )

        assertFalse(builtInOnly.containsKey("toolConfig"))
        assertFalse(functionOnly.containsKey("toolConfig"))
    }

    @Test
    fun `server tool context and function id survive streaming and history replay`() {
        val toolCallPart = buildJsonObject {
            put("thoughtSignature", "search-call-signature")
            put("toolCall", buildJsonObject {
                put("toolType", "GOOGLE_SEARCH_WEB")
                put("args", buildJsonObject {
                    putJsonArray("queries") { add("latest weather") }
                })
                put("id", "server-call-1")
            })
        }
        val toolResponsePart = buildJsonObject {
            put("thoughtSignature", "search-response-signature")
            put("toolResponse", buildJsonObject {
                put("toolType", "GOOGLE_SEARCH_WEB")
                put("response", buildJsonObject {
                    put("search_suggestions", "weather result")
                })
                put("id", "server-call-1")
            })
        }
        val functionCallPart = buildJsonObject {
            put("functionCall", buildJsonObject {
                put("name", "save_weather")
                put("args", buildJsonObject { put("value", "sunny") })
                put("id", "function-call-1")
            })
            put("thoughtSignature", "function-signature")
        }
        val event = SseEvent(data = json.encodeToString(buildJsonObject {
            putJsonArray("candidates") {
                add(buildJsonObject {
                    put("content", buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray {
                            add(toolCallPart)
                            add(toolResponsePart)
                            add(functionCallPart)
                        })
                    })
                })
            }
        }))

        val decoder = GoogleStreamDecoder("response-1", "gemini-3.7-flash")
        val handler = StreamChunkHandler(toolModel(tools = setOf(BuiltInTools.Search)))
        var messages = listOf(UIMessage.user("search and save"))
        decoder.accept(event).chunks.forEach { messages = handler.handle(messages, it) }
        decoder.onClosed().forEach { messages = handler.handle(messages, it) }

        val assistant = messages.last()
        val serverTool = assistant.parts.filterIsInstance<UIMessagePart.ServerTool>().single()
        assertEquals(ServerToolStatus.COMPLETED, serverTool.status)
        assertEquals("server-call-1", serverTool.toolCallId)
        assertEquals(
            ServerToolProtocol.GOOGLE_GENERATE_CONTENT,
            serverTool.metadataAs<ServerToolMetadata>()?.protocol
        )
        assertEquals(toolCallPart, serverTool.metadataAs<ServerToolMetadata>()?.call)
        assertEquals(toolResponsePart, serverTool.metadataAs<ServerToolMetadata>()?.result)

        val functionTool = assistant.parts.filterIsInstance<UIMessagePart.Tool>().single()
        assertEquals("function-call-1", functionTool.toolCallId)

        val executedAssistant = assistant.copy(parts = assistant.parts.map { part ->
            if (part is UIMessagePart.Tool) {
                part.copy(output = listOf(UIMessagePart.Text("saved")))
            } else {
                part
            }
        })
        val contents = invokeBuildContents(listOf(messages.first(), executedAssistant))
        val modelParts = contents[1].jsonObject["parts"]!!.jsonArray
        assertEquals(toolCallPart, modelParts[0])
        assertEquals(toolResponsePart, modelParts[1])
        assertEquals(
            "function-call-1",
            modelParts[2].jsonObject["functionCall"]!!.jsonObject["id"]!!.jsonPrimitive.content
        )
        assertEquals(
            "function-call-1",
            contents[2].jsonObject["parts"]!!.jsonArray.single()
                .jsonObject["functionResponse"]!!.jsonObject["id"]!!.jsonPrimitive.content
        )
    }

    private fun invokeBuildRequestBody(
        providerSetting: ProviderSetting.Google,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): JsonObject {
        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildCompletionRequestBody",
            ProviderSetting.Google::class.java,
            List::class.java,
            TextGenerationParams::class.java,
        )
        method.isAccessible = true
        return method.invoke(provider, providerSetting, messages, params) as JsonObject
    }

    private fun invokeBuildContents(messages: List<UIMessage>): JsonArray {
        val method = GoogleProvider::class.java.getDeclaredMethod("buildContents", List::class.java)
        method.isAccessible = true
        return method.invoke(provider, messages) as JsonArray
    }

    private fun toolModel(tools: Set<BuiltInTools> = emptySet()) = Model(
        modelId = "gemini-3.7-flash",
        abilities = listOf(ModelAbility.TOOL),
        tools = tools,
    )

    private fun testTool() = Tool(
        name = "save_weather",
        description = "Save weather information",
        execute = { emptyList() },
    )
}
