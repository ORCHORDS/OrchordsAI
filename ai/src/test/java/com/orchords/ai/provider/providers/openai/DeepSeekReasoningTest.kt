package com.orchords.ai.provider.providers.openai

import com.orchords.ai.core.ReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekReasoningTest {
    @Test
    fun `deepseek v4 maps xhigh and max to max`() {
        assertEquals(
            "max",
            normalizeDeepSeekCompatibleReasoningEffort("deepseek-v4-flash", ReasoningLevel.XHIGH),
        )
        assertEquals(
            "max",
            normalizeDeepSeekCompatibleReasoningEffort("deepseek-ai/DeepSeek-V4-Pro", ReasoningLevel.MAX),
        )
    }

    @Test
    fun `deepseek v4 leaves lower effort levels unchanged`() {
        assertEquals(
            "low",
            normalizeDeepSeekCompatibleReasoningEffort("deepseek-v4-flash", ReasoningLevel.LOW),
        )
        assertEquals(
            "medium",
            normalizeDeepSeekCompatibleReasoningEffort("deepseek-v4-flash", ReasoningLevel.MEDIUM),
        )
        assertEquals(
            "high",
            normalizeDeepSeekCompatibleReasoningEffort("deepseek-v4-flash", ReasoningLevel.HIGH),
        )
    }

    @Test
    fun `non deepseek models preserve xhigh`() {
        assertEquals(
            "xhigh",
            normalizeDeepSeekCompatibleReasoningEffort("provider-model-that-supports-xhigh", ReasoningLevel.XHIGH),
        )
    }
}
