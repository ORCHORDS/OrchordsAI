package com.orchords.orchordsai.data.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeFilePathsTest {
    private val root = Files.createTempDirectory("safe-file-paths").toFile()

    @Test
    fun `resolves nested relative path inside root`() {
        assertEquals(
            File(root, "nested/document.txt").canonicalFile,
            SafeFilePaths.resolveInside(root, "nested/document.txt"),
        )
    }

    @Test
    fun `rejects traversal absolute sibling and invalid paths`() {
        val sibling = File(root.parentFile, "${root.name}-other/outside.txt").absolutePath

        listOf(
            "../outside.txt",
            "nested/../../outside.txt",
            "..\\outside.txt",
            sibling,
            "",
            "bad\u0000name",
        ).forEach { path ->
            assertNull(path, SafeFilePaths.resolveInside(root, path))
        }
    }

    @Test
    fun `direct child resolver rejects nested paths`() {
        assertEquals(
            File(root, "font.ttf").canonicalFile,
            SafeFilePaths.resolveDirectChild(root, "font.ttf"),
        )
        assertNull(SafeFilePaths.resolveDirectChild(root, "nested/font.ttf"))
        assertNull(SafeFilePaths.resolveDirectChild(root, "..\\font.ttf"))
    }
}
