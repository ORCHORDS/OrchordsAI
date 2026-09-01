package com.orchords.orchordsai.data.files

import java.io.File
import java.io.IOException

internal object SafeFilePaths {
    fun resolveInside(root: File, relativePath: String): File? {
        if (relativePath.isBlank() || '\u0000' in relativePath || '\\' in relativePath) return null
        val relative = File(relativePath)
        if (relative.isAbsolute) return null

        return try {
            val canonicalRoot = root.canonicalFile
            val target = File(canonicalRoot, relativePath).canonicalFile
            val rootPrefix = canonicalRoot.path + File.separator
            target.takeIf { it.path.startsWith(rootPrefix) }
        } catch (_: IOException) {
            null
        }
    }

    fun resolveDirectChild(root: File, fileName: String): File? {
        val canonicalRoot = try {
            root.canonicalFile
        } catch (_: IOException) {
            return null
        }
        return resolveInside(canonicalRoot, fileName)
            ?.takeIf { it.parentFile == canonicalRoot }
    }
}
