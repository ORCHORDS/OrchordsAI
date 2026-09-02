package com.orchords.orchordsai.data.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class ManagedFilePathPolicyTest {
    @Test
    fun `contained managed path resolves canonically even when file is missing`() {
        val root = Files.createTempDirectory("managed-file-path").toFile()
        try {
            val file = File(root, "upload/fixture.txt").apply {
                parentFile!!.mkdirs()
                writeText("fixture")
            }

            assertEquals(file.canonicalFile, resolveManagedFilePath(root, "upload/fixture.txt"))
            assertEquals(
                File(root, "upload/missing.txt").canonicalFile,
                resolveManagedFilePath(root, "upload/missing.txt"),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `dot segments cannot escape managed files root`() {
        val parent = Files.createTempDirectory("managed-file-path-parent").toFile()
        val root = File(parent, "files").apply { mkdirs() }
        try {
            assertNull(resolveManagedFilePath(root, "../outside.txt"))
            assertNull(resolveManagedFilePath(root, "upload/../../outside.txt"))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `app files root itself is never a managed file target`() {
        val root = Files.createTempDirectory("managed-file-path").toFile()
        try {
            assertNull(resolveManagedFilePath(root, "."))
            assertNull(resolveManagedFilePath(root, "upload/.."))
            assertNull(resolveManagedFilePath(root, ""))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `absolute drive and UNC shaped metadata is rejected`() {
        val root = Files.createTempDirectory("managed-file-path").toFile()
        try {
            assertNull(resolveManagedFilePath(root, File(root, "fixture.txt").absolutePath))
            assertNull(resolveManagedFilePath(root, "/etc/passwd"))
            assertNull(resolveManagedFilePath(root, "C:\\temp\\fixture.txt"))
            assertNull(resolveManagedFilePath(root, "\\\\server\\share\\fixture.txt"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `symlink parent cannot redirect managed path outside root`() {
        val parent = Files.createTempDirectory("managed-file-path-parent").toFile()
        val root = File(parent, "files").apply { mkdirs() }
        val outside = File(parent, "outside").apply { mkdirs() }
        val link = File(root, "upload/link")
        try {
            link.parentFile!!.mkdirs()
            val symlinkCreated = runCatching {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            }.isSuccess
            assumeTrue("Filesystem does not support test symlinks", symlinkCreated)
            File(outside, "secret.txt").writeText("outside")

            assertNull(resolveManagedFilePath(root, "upload/link/secret.txt"))
        } finally {
            parent.deleteRecursively()
        }
    }
}
