package com.orchords.orchordsai.web.routes

import com.orchords.orchordsai.data.db.entity.ManagedFileEntity
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
 * every dereference must re-check absolute-path forms and canonical containment at read time.
 */
internal fun resolveManagedWebFileById(
    filesDir: File,
    managedFile: ManagedFileEntity?,
): File? {
    val relativePath = managedFile?.relativePath ?: return null
    if (relativePath.contains('\u0000')) return null
    if (
        File(relativePath).isAbsolute ||
        relativePath.startsWith('/') ||
        relativePath.startsWith("\\\\") ||
        Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(relativePath)
    ) return null

    val root = filesDir.canonicalFile
    val target = File(root, relativePath).canonicalFile
    val rootPath = root.path
    val targetPath = target.path
    val isContained = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)

    return target.takeIf { isContained && it.isFile }
}
