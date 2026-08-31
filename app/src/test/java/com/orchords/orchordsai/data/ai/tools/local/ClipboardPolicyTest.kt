package com.orchords.orchordsai.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardPolicyTest {
    @Test
    fun `clipboard read does not require write approval`() {
        val args = buildJsonObject { put("action", "read") }
        assertFalse(clipboardNeedsApproval(args))
    }

    @Test
    fun `clipboard write requires approval`() {
        val args = buildJsonObject {
            put("action", "write")
            put("text", "replacement")
        }
        assertTrue(clipboardNeedsApproval(args))
    }

    @Test
    fun `unknown clipboard action fails closed`() {
        val args = buildJsonObject { put("action", "future_action") }
        assertTrue(clipboardNeedsApproval(args))
    }
}
