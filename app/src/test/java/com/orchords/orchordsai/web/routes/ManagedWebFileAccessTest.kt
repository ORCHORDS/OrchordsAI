package com.orchords.orchordsai.web.routes

import com.orchords.orchordsai.data.db.entity.ManagedFileEntity
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedWebFileAccessTest {
    @Test
    fun `managed file resolves only for exact registered relative path`() {
        val root = Files.createTempDirectory("managed-web-file").toFile()
        try {
            val file = File(root, "upload/fixture.txt").apply {
                parentFile!!.mkdirs()
                writeText("fixture")
            }
            val entity = managedFile("upload/fixture.txt")

            assertEquals(file.canonicalFile, resolveManagedWebFile(root, "upload/fixture.txt", entity))
            assertNull(resolveManagedWebFile(root, "upload/other.txt", entity))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `unregistered internal file is never web readable`() {
        val root = Files.createTempDirectory("managed-web-file").toFile()
        try {
            File(root, "tool_outputs/private.txt").apply {
                parentFile!!.mkdirs()
                writeText("private-tool-output")
            }

            assertNull(resolveManagedWebFile(root, "tool_outputs/private.txt", null))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `stale managed record is rejected when disk file is missing`() {
        val root = Files.createTempDirectory("managed-web-file").toFile()
        try {
            assertNull(
                resolveManagedWebFile(
                    root,
                    "upload/missing.txt",
                    managedFile("upload/missing.txt"),
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `managed record cannot escape app files root through dot segments`() {
        val parent = Files.createTempDirectory("managed-web-file-parent").toFile()
        val root = File(parent, "files").apply { mkdirs() }
        val outside = File(parent, "outside.txt").apply { writeText("outside") }
        try {
            val relativePath = "../${outside.name}"
            assertNull(resolveManagedWebFile(root, relativePath, managedFile(relativePath)))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `absolute managed path is rejected`() {
        val root = Files.createTempDirectory("managed-web-file").toFile()
        try {
            val absolute = File(root, "fixture.txt").absolutePath
            assertNull(resolveManagedWebFile(root, absolute, managedFile(absolute)))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun managedFile(relativePath: String) = ManagedFileEntity(
        folder = relativePath.substringBefore('/', missingDelimiterValue = "upload"),
        relativePath = relativePath,
        displayName = relativePath.substringAfterLast('/'),
        mimeType = "text/plain",
        sizeBytes = 7,
        createdAt = 1,
        updatedAt = 1,
    )
}
