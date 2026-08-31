package com.orchords.orchordsai.data.ai.tools

private val WRITABLE_ROOTFS_ROOTS = listOf("/workspace", "/tmp")

/**
 * Canonical lexical identity for an absolute path inside the Workspace Rootfs namespace.
 * This deliberately does not resolve host filesystem paths or follow symlinks.
 */
internal fun normalizeRootfsPath(rawPath: String): String {
    val path = rawPath.replace('\\', '/').trim()
    require(path.isNotBlank()) { "path is required" }
    require(path.startsWith('/')) { "path must be an absolute path inside Rootfs" }
    require(!path.contains('\u0000')) { "path contains invalid character" }

    val segments = ArrayDeque<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> {
                require(segments.isNotEmpty()) { "path escapes Rootfs" }
                segments.removeLast()
            }
            else -> segments.addLast(segment)
        }
    }
    return if (segments.isEmpty()) "/" else segments.joinToString(separator = "/", prefix = "/")
}

internal fun isOutsideWritableRootfsRoots(rawPath: String): Boolean {
    val path = normalizeRootfsPath(rawPath)
    return WRITABLE_ROOTFS_ROOTS.none { root ->
        path == root || path.startsWith("$root/")
    }
}
