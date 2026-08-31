package com.orchords.orchordsai.data.ai.transformers

import com.orchords.ai.ui.UIMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderPolicyTest {
    @Test
    fun `ordinary conversation text cannot trigger runtime placeholder expansion`() {
        assertFalse(shouldExpandRuntimePlaceholders(UIMessage.user("{{device_info}}")))
    }

    @Test
    fun `synthetic product prompt text may use runtime placeholders`() {
        val message = UIMessage.system("{{cur_date}}").copy(isSynthetic = true)
        assertTrue(shouldExpandRuntimePlaceholders(message))
    }
}
