package com.orchords.orchordsai.data.db

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.orchords.orchordsai.data.db.dao.ManagedFileDAO
import com.orchords.orchordsai.data.db.entity.ManagedFileEntity
import com.orchords.orchordsai.data.files.FilesManager

/**
 * Coordinates externalized JSON payloads for [com.orchords.orchordsai.data.db.entity.MessageNodeEntity].
 *
 * Inline storage vs. file-backed is decided by [MessageNodePayloadStore.MAX_INLINE_BYTES].
 * Anything larger than the threshold is written under
 * `filesDir/managed/payloads/<uuid>-<nodeId>.json` and tracked via a
 * [ManagedFileEntity] whose `id` is referenced from
 * [com.orchords.orchordsai.data.db.entity.MessageNodeEntity.payloadBlobId].
 *
 * The store is intentionally minimal: callers (the repository) hold the
 * transactional responsibility and pass in the **previous** payload id when
 * deleting orphans so a partial-failure write cannot strand a fresh blob.
 *
 * See issue #345.
 */
class MessageNodePayloadStore(
    private val filesManager: FilesManager,
    private val managedFileDao: ManagedFileDAO,
) {
    /**
     * If [json] fits inline, returns `null` and performs no I/O.
     * Otherwise persists the JSON via [FilesManager.saveManagedText] and
     * returns the freshly inserted [ManagedFileEntity.id].
     */
    suspend fun store(nodeId: String, json: String): Long? {
        if (!MessageNodePayloadResolver.shouldExternalize(json)) return null
        val entity = filesManager.saveManagedText(
            folder = PAYLOAD_FOLDER,
            text = json,
            displayName = "$nodeId.json",
            mimeType = MIME_TYPE,
        )
        return entity.id
    }

    /**
     * Resolves the JSON for a row. Returns `null` when [payloadBlobId] is null
     * (caller should fall back to the inline `messages` column) or when the
     * underlying managed file is missing / unreadable (treat as a corrupt row
     * at the loader boundary).
     */
    suspend fun load(payloadBlobId: Long?): String? {
        if (payloadBlobId == null) return null
        val entity = managedFileDao.getById(payloadBlobId) ?: return null
        val file = filesManager.getFileOrNull(entity) ?: return null
        return runCatching {
            withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
        }.onFailure {
            Log.w(TAG, "load: failed to read payload blob id=$payloadBlobId", it)
        }.getOrNull()
    }

    /** Best-effort delete of an externalized blob by id; no-op for null. */
    suspend fun delete(payloadBlobId: Long?) {
        if (payloadBlobId == null) return
        runCatching {
            filesManager.delete(payloadBlobId, deleteFromDisk = true)
        }.onFailure {
            Log.w(TAG, "delete: failed to remove payload blob id=$payloadBlobId", it)
        }
    }

    companion object {
        private const val TAG = "MessageNodePayloadStore"

        /** Folder under `filesDir` for externalized node JSON. */
        const val PAYLOAD_FOLDER = "payloads"

        /** MIME type for stored payloads. */
        const val MIME_TYPE = "application/json"

        /** 256 KiB threshold per node; matches the CursorWindow budget after the reflection hack is removed. */
        const val MAX_INLINE_BYTES: Int = 256 * 1024
    }
}
