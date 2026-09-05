package com.orchords.ai.provider

import com.orchords.ai.core.Tool
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart

/** Existing Google declaration safeguard. Other route limits remain a capability-profile task. */
internal const val GEMINI_HARD_TOOL_CAP: Int = 512

/**
 * One preflight used by all four provider serializers. Completed historical calls do not
 * permanently pin declarations. Incomplete calls retain priority inside the supplied registry;
 * selection cannot re-enable a revoked/missing tool or grant permission to execute it.
 *
 * A required set that exceeds the cap is incompatible, not a reason to drop one of its tools.
 * Unknown per-route limits are NOT a claim of unlimited vendor support. Relevance routing,
 * explicit workflow pins, route profiles and canonical alias collision checks remain #359/#197.
 */
internal fun resolveToolBudget(
    tools: List<Tool>,
    messages: List<UIMessage>,
    providerSetting: ProviderSetting?,
): List<Tool> {
    val requiredNames = messages.asSequence()
        .flatMap { it.parts.asSequence() }
        .filterIsInstance<UIMessagePart.Tool>()
        .filterNot { it.isExecuted }
        .map { it.toolName }
        .toSet()
    val userCap = when (providerSetting) {
        is ProviderSetting.Google -> providerSetting.maxToolsPerRequest
        is ProviderSetting.OpenAI -> providerSetting.maxToolsPerRequest
        is ProviderSetting.Claude -> providerSetting.maxToolsPerRequest
        null -> null
    }
    return selectBudgetedTools(
        tools = tools,
        name = { it.name },
        requiredNames = requiredNames,
        hardCap = if (providerSetting is ProviderSetting.Google) GEMINI_HARD_TOOL_CAP else null,
        userCap = userCap,
    )
}
