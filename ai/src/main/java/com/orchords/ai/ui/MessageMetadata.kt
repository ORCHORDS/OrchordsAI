package com.orchords.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import com.orchords.ai.util.json

/**
 *
 *
 */
sealed interface PartMetadata

/**
 */
@Serializable
data class ClaudeReasoningMetadata(
    val signature: String? = null,
) : PartMetadata

/**
 */
@Serializable
data class OpenAIReasoningMetadata(
    @SerialName("reasoning_id")
    val reasoningId: String? = null,
    @SerialName("encrypted_content")
    val encryptedContent: String? = null,
) : PartMetadata

/**
 */
@Serializable
enum class ServerToolProtocol {
    @SerialName("openai_responses")
    OPENAI_RESPONSES,

    @SerialName("anthropic_messages")
    ANTHROPIC_MESSAGES,

    @SerialName("google_generate_content")
    GOOGLE_GENERATE_CONTENT,
}

/**
 *
 */
@Serializable
data class ServerToolMetadata(
    @SerialName("server_tool_protocol")
    val protocol: ServerToolProtocol? = null,
    @SerialName("server_tool_call")
    val call: JsonObject? = null,
    @SerialName("server_tool_call_index")
    val callIndex: Int? = null,
    @SerialName("server_tool_result")
    val result: JsonObject? = null,
    @SerialName("server_tool_result_index")
    val resultIndex: Int? = null,
) : PartMetadata

/**
 */
@Serializable
data class OpenRouterReasoningMetadata(
    @SerialName("openrouter_reasoning_details")
    val reasoningDetails: JsonArray? = null,
) : PartMetadata

/**
 */
@Serializable
data class GoogleThoughtMetadata(
    val thoughtSignature: String? = null,
) : PartMetadata

/**
 */
@Serializable
data class DiffMetadata(
    val diff: String? = null,
) : PartMetadata

/**
 *
 */
inline fun <reified T : PartMetadata> UIMessagePart.metadataAs(): T? = metadata?.let {
    runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull()
}

/**
 *
 */
inline fun <reified T : PartMetadata> T.toMetadata(): JsonObject =
    json.encodeToJsonElement(this).jsonObject
