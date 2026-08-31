package com.orchords.orchordsai.data.sync

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

internal fun newBackupFileName(
    now: LocalDateTime = LocalDateTime.now(),
    runId: UUID = UUID.randomUUID(),
): String = "backup_${now.format(BACKUP_TIMESTAMP_FORMAT)}_${runId}.zip"
