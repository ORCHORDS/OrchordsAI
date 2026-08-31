package com.orchords.orchordsai.data.sync

import java.time.LocalDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupRunIdentityTest {
    @Test
    fun `same timestamp with different run ids produces different backup names`() {
        val now = LocalDateTime.of(2026, 9, 1, 3, 0, 0)

        val first = newBackupFileName(
            now = now,
            runId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        )
        val second = newBackupFileName(
            now = now,
            runId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        )

        assertNotEquals(first, second)
        assertEquals(
            "backup_20260901_030000_00000000-0000-0000-0000-000000000001.zip",
            first,
        )
    }

    @Test
    fun `backup name remains compatible with backup prefix and zip suffix discovery`() {
        val name = newBackupFileName(
            now = LocalDateTime.of(2026, 9, 1, 3, 0, 0),
            runId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        )

        assertEquals(true, name.startsWith("backup_"))
        assertEquals(true, name.endsWith(".zip"))
    }
}
