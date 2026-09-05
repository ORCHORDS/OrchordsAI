package com.orchords.ai.provider

import com.orchords.ai.core.Tool
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart

/**
 * Hard vendor cap on Gemini generateContent function declarations,
 * verified from `googleapis/python-genai` `Tool.function_declarations`
 * docstring ("At most 512 function declarations can be specified.") and
 * https://ai.google.dev/api/generate-content. Other vendors (OpenAI Chat
 * Completions / Responses, Anthropic Messages, OpenRouter) do not publish
 * a hard cap; their cap is user-configurable per
 * `ProviderSetting.maxToolsPerRequest`.
 *
 * See issue #359.
 */
internal const val GEMINI_HARD_TOOL_CAP: Int = 512

/**
 * Computes the tools to send on the wire for one provider request.
 *
 * Two layers of budget are applied in order:
 *  1. **Hard vendor cap** — currently only Gemini (`[GEMINI_HARD_TOOL_CAP]`).
 *  2. **User-configurable cap** — `providerSetting.maxToolsPerRequest`
 *     (default `null` ⇒ no cap, today's behavior).
 *
 * In addition, a **continuation invariant** is enforced: any tool whose call
 * is already mid-flight in [messages] is lifted to the front of the returned
 * list, before either cap is applied. Without this, truncation could remove
 * the very tool whose result the model is supposed to emit next, which would
 * fail the request.
 *
 * Duplicates in [tools] are collapsed by `Tool.name`, matching the existing
 * identity used throughout the wire builders (`Tool.name` is what shows up
 * on the wire and in `UIMessagePart.Tool.toolName`).
 */
internal fun resolveToolBudget(
    tools: List<Tool>,
    messages: List<UIMessage>,
    providerSetting: ProviderSetting?,
): List<Tool> {
    if (tools.isEmpty()) return tools

    // Dedup by name; preserve first-seen order.
    val byName: List<Tool> = tools.distinctBy { it.name }
    if (byName.isEmpty()) return byName

    // (1) Continuation invariant: tools already invoked in the conversation
    //     must remain available so the model can produce their results.
    val inFlightToolNames: Set<String> = messages.flatMap { it.parts }
        .filterIsInstance<UIMessagePart.Tool>()
        .map { it.toolName }
        .toSet()

    val ordered: List<Tool> = if (inFlightToolNames.isEmpty()) {
        byName
    } else {
        val priority = byName.filter { it.name in inFlightToolNames }
        val rest = byName.filter { it.name !in inFlightToolNames }
        // Stable order within each partition; in-flight tools come first.
        priority + rest
    }

    // (2) Hard vendor cap. Today only Gemini publishes one.
    val afterVendorCap: List<Tool> = when (providerSetting) {
        is ProviderSetting.Google -> ordered.take(GEMINI_HARD_TOOL_CAP)
        is ProviderSetting.OpenAI,
        is ProviderSetting.Claude,
        null -> ordered
    }

    // (3) User-configurable cap (tighter-than-vendor wins).
    val userCap: Int? = when (providerSetting) {
        is ProviderSetting.Google -> providerSetting.maxToolsPerRequest
        is ProviderSetting.OpenAI -> providerSetting.maxToolsPerRequest
        is ProviderSetting.Claude -> providerSetting.maxToolsPerRequest
        null -> null
    }
    return if (userCap == null) afterVendorCap else afterVendorCap.take(userCap)
}
