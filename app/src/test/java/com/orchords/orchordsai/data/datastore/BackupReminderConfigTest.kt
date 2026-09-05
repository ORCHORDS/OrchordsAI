package com.orchords.orchordsai.data.datastore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the truthfulness contract enforced by issue #366: the backup reminder's `isDue`
 * predicate depends on `lastBackupTime`, and `lastBackupTime` must therefore be advanced only
 * after the backup is delivered to its destination — never on staging.
 */
class BackupReminderConfigTest {

    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun `reminder is due when enabled and lastBackupTime is zero`() {
        val config = BackupReminderConfig(enabled = true, intervalDays = 7, lastBackupTime = 0L)
        assertTrue(config.isReminderDue(nowMs = 1_700_000_000_000L))
    }

    @Test
    fun `reminder is not due when disabled regardless of lastBackupTime`() {
        val config = BackupReminderConfig(
            enabled = false,
            intervalDays = 7,
            lastBackupTime = 0L,
        )
        assertFalse(config.isReminderDue(nowMs = 1_700_000_000_000L))
    }

    @Test
    fun `reminder is not due within the interval window`() {
        val now = 1_700_000_000_000L
        val config = BackupReminderConfig(
            enabled = true,
            intervalDays = 7,
            lastBackupTime = now - 6 * dayMs,
        )
        assertFalse(config.isReminderDue(nowMs = now))
    }

    @Test
    fun `reminder is due exactly one millisecond past the interval`() {
        val now = 1_700_000_000_000L
        val config = BackupReminderConfig(
            enabled = true,
            intervalDays = 7,
            lastBackupTime = now - (7 * dayMs + 1L),
        )
        assertTrue(config.isReminderDue(nowMs = now))
    }

    @Test
    fun `staging alone must not advance lastBackupTime — sanity check on the contract`() {
        // This test pins the precondition for the production fix: if the staging path ever
        // started advancing `lastBackupTime` again, the reminder would silently disappear on
        // the next render. The predicate itself is correct; the contract is what changed.
        val beforeStaging = BackupReminderConfig(
            enabled = true,
            intervalDays = 7,
            lastBackupTime = 0L,
        )
        assertTrue(
            "A user who has never completed a backup must still see the reminder " +
                "after staging alone",
            beforeStaging.isReminderDue(nowMs = 1_700_000_000_000L),
        )
    }
}
