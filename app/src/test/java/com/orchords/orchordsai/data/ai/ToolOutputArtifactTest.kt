package com.orchords.orchordsai.data.ai

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOutputArtifactTest {
    @Test
    fun `tool output artifact always stays inside its managed root`() {
        val parent = Files.createTempDirectory("tool-output-artifact").toFile()
        val outputRoot = File(parent, "tool_outputs")
        val outside = File(parent, "escape.txt").apply { writeText("sentinel") }
        try {
            val artifact = persistToolOutputArtifact(outputRoot, "full tool result")

            assertEquals(outputRoot.canonicalFile, artifact.parentFile.canonicalFile)
            assertTrue(artifact.isFile)
            assertEquals("full tool result", artifact.readText())
            assertEquals("sentinel", outside.readText())
            assertFalse(artifact.name.contains('/'))
            assertFalse(artifact.name.contains('\\'))
            assertFalse(artifact.name.contains(".."))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `separate tool results cannot overwrite through provider identity collisions`() {
        val outputRoot = Files.createTempDirectory("tool-output-artifact").toFile()
        try {
            val first = persistToolOutputArtifact(outputRoot, "first")
            val second = persistToolOutputArtifact(outputRoot, "second")

            assertNotEquals(first.canonicalFile, second.canonicalFile)
            assertEquals("first", first.readText())
            assertEquals("second", second.readText())
        } finally {
            outputRoot.deleteRecursively()
        }
    }
}
