package com.orchords.orchordsai.data.sync

import java.io.File

private const val MAX_BACKUP_DISPLAY_NAME_LENGTH = 160
private val BACKUP_DISPLAY_NAME = Regex("^backup_[A-Za-z0-9._-]+\\.zip$")

internal fun requireSafeBackupDisplayName(displayName: String): String {
    require(displayName.length in 1..MAX_BACKUP_DISPLAY_NAME_LENGTH) {
        "Invalid remote backup name"
    }
    require(BACKUP_DISPLAY_NAME.matches(displayName)) {
        "Invalid remote backup name"
    }
    return displayName
}

internal fun resolveBackupCacheFile(cacheDir: File, displayName: String): File {
    val safeName = requireSafeBackupDisplayName(displayName)
    val root = cacheDir.canonicalFile
    val target = File(root, safeName).canonicalFile
    require(target.parentFile == root) {
        "Invalid remote backup cache path"
    }
    return target
}
