package com.orchords.orchordsai.data.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSnapshotServiceTest {
    @Test
    fun `authoritative database identity matches backup entry`() {
        assertEquals("orchordsai.db", APP_DATABASE_NAME)
        assertEquals(APP_DATABASE_NAME, DATABASE_BACKUP_ENTRY)
    }

    @Test
    fun `sqlite header validator accepts a database header`() {
        val file = File.createTempFile("sqlite-header-", ".db")
        try {
            file.writeBytes("SQLite format 3\u0000fixture".toByteArray(Charsets.US_ASCII))
            assertTrue(hasSQLiteFileHeader(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `sqlite header validator rejects malformed restore input`() {
        val file = File.createTempFile("sqlite-invalid-", ".db")
        try {
            file.writeText("not a database")
            assertFalse(hasSQLiteFileHeader(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `sqlite header validator rejects missing snapshot`() {
        val file = File(System.getProperty("java.io.tmpdir"), "missing-${System.nanoTime()}.db")
        assertFalse(hasSQLiteFileHeader(file))
    }
}
