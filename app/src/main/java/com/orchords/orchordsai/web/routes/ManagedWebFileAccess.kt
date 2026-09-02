package com.orchords.orchordsai.web.routes

import com.orchords.orchordsai.data.db.entity.ManagedFileEntity
import com.orchords.orchordsai.data.files.resolveManagedFilePath
import java.io.File

/**
 * Resolves a legacy Web relative-path request only when it names the exact path of a managed file
 * and that managed path still canonicalizes beneath the app-managed files root.
 *
 * A provider/model supplied path or an arbitrary app-private file is never sufficient authority.
 */
internal fun resolveManagedWebFile(
    filesDir: File,
    requestedRelativePath: String,
    managedFile: ManagedFileEntity?,
): File? {
    if (managedFile == null || managedFile.relativePath != requestedRelativePath) return null
    return resolveManagedWebFileById(filesDir, managedFile)
}

/**
 * Resolves a managed-file record for ID-based Web download without trusting persisted relativePath
 * as filesystem authority. Backup/import/corruption can make a database row stale or malicious, so
 * every dereference shares the canonical managed-file path policy before bytes are served.
 */
internal fun resolveManagedWebFileById(
    filesDir: File,
    managedFile: ManagedFileEntity?,
): File? {
    val relativePath = managedFile?.relativePath ?: return null
    return resolveManagedFilePath(filesDir, relativePath)?.takeIf { it.isFile }
}
