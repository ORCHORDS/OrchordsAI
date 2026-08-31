package com.orchords.ai.provider.providers.openai

import com.orchords.ai.core.ReasoningLevel

internal fun normalizeDeepSeekCompatibleReasoningEffort(
    modelId: String,
    level: ReasoningLevel,
): String {
    val isDeepSeekV4 = "deepseek-v4" in modelId.lowercase()
    return if (isDeepSeekV4 && (level == ReasoningLevel.XHIGH || level == ReasoningLevel.MAX)) {
        "max"
    } else {
        level.effort
    }
}
