package com.orchords.orchordsai.data.files

import java.io.File

/**
 * Resolves persisted managed-file metadata without letting the stored relative path become
 * filesystem authority. The returned path is canonical, remains strictly below [filesDir], and
 * therefore cannot target the app files root itself or escape through dot segments/symlink parents.
 */
internal fun resolveManagedFilePath(filesDir: File, relativePath: String): File? {
    if (relativePath.isBlank() || relativePath.contains('\u0000')) return null
    if (
        File(relativePath).isAbsolute ||
        relativePath.startsWith('/') ||
        relativePath.startsWith("\\\\") ||
        Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(relativePath)
    ) return null

    val root = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
    val target = runCatching { File(root, relativePath).canonicalFile }.getOrNull() ?: return null
    val rootPath = root.path
    val targetPath = target.path

    return target.takeIf { targetPath.startsWith(rootPath + File.separator) }
}
