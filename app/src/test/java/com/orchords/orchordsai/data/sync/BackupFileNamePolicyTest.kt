package com.orchords.orchordsai.data.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFileNamePolicyTest {
    @Test
    fun `generated and legacy backup basenames are accepted`() {
        assertEquals(
            "backup_20260901_030000.zip",
            requireSafeBackupDisplayName("backup_20260901_030000.zip"),
        )
        assertEquals(
            "backup_20260901_030000_00000000-0000-0000-0000-000000000001.zip",
            requireSafeBackupDisplayName(
                "backup_20260901_030000_00000000-0000-0000-0000-000000000001.zip"
            ),
        )
    }

    @Test
    fun `path separators and traversal names are rejected`() {
        listOf(
            "backup_../../escape.zip",
            "backup_..\\..\\escape.zip",
            "folder/backup_20260901_030000.zip",
            "backup_../escape.zip",
        ).forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                requireSafeBackupDisplayName(name)
            }
        }
    }

    @Test
    fun `cache resolution remains a direct child of the cache directory`() {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "orchords-backup-name-test")
        val resolved = resolveBackupCacheFile(cacheDir, "backup_20260901_030000.zip")

        assertEquals(cacheDir.canonicalFile, resolved.parentFile?.canonicalFile)
    }
}
