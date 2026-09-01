package com.orchords.orchordsai.data.ai.tools

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryToolsPromptCacheTest {
    @Test
    fun `memory tool description does not embed current date`() {
        val tool = buildMemoryTools(
            json = Json,
            onCreation = { error("not executed") },
            onUpdate = { _, _ -> error("not executed") },
            onDelete = { error("not executed") },
        ).single()

        assertFalse(tool.description.contains("Today is"))
    }
}
