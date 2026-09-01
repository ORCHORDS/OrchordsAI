package com.orchords.orchordsai.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.orchords.orchordsai.data.db.APP_DATABASE_SCHEMA_VERSION
import com.orchords.orchordsai.data.db.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val APP_DATABASE_NAME = "orchordsai.db"
const val DATABASE_BACKUP_ENTRY = APP_DATABASE_NAME

private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

internal fun hasSQLiteFileHeader(file: File): Boolean {
    if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
    val header = ByteArray(SQLITE_HEADER.size)
    FileInputStream(file).use { input ->
        if (input.read(header) != header.size) return false
    }
    return header.contentEquals(SQLITE_HEADER)
}

internal fun isValidDatabaseSnapshot(file: File): Boolean {
    if (!hasSQLiteFileHeader(file)) return false
    return runCatching {
        val snapshotDatabase = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            val integrityOk = snapshotDatabase.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            val schemaVersion = snapshotDatabase.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else -1
            }
            val requiredTables = mutableSetOf<String>()
            snapshotDatabase.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name IN ('ConversationEntity', 'conversation_folder', 'message_node')",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) requiredTables += cursor.getString(0)
            }
            integrityOk &&
                schemaVersion == APP_DATABASE_SCHEMA_VERSION &&
                requiredTables.containsAll(
                    setOf("ConversationEntity", "conversation_folder", "message_node")
                )
        } finally {
            snapshotDatabase.close()
        }
    }.getOrDefault(false)
}

private fun requireValidDatabaseSnapshot(file: File) {
    require(isValidDatabaseSnapshot(file)) {
        "Database snapshot is malformed, corrupt, or incompatible with this app schema"
    }
}

/**
 * Owns the on-device SQLite snapshot boundary used by every backup transport.
 *
 * `VACUUM INTO` runs through Room's live connection, so committed WAL state is
 * included in one transactionally consistent database image while freelist
 * pages and deleted-row residue are omitted from the exported snapshot.
 */
@Singleton
class DatabaseSnapshotService @Inject constructor(
    private val context: Context,
    private val database: AppDatabase,
) {
    fun liveDatabaseFile(): File = context.getDatabasePath(APP_DATABASE_NAME)

    suspend fun createSnapshot(): File = withContext(Dispatchers.IO) {
        val snapshot = File.createTempFile("orchordsai-snapshot-", ".db", context.cacheDir)
        // VACUUM INTO refuses to overwrite an existing file, including the empty
        // file created by createTempFile(). Reserve a unique pathname, then let
        // SQLite create the database itself.
        check(snapshot.delete()) { "Unable to reserve database snapshot path" }
        try {
            database.openHelper.writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf(snapshot.absolutePath)
            )
            requireValidDatabaseSnapshot(snapshot)
            snapshot
        } catch (t: Throwable) {
            snapshot.delete()
            throw t
        }
    }

    /**
     * Validate and durably stage an incoming database before the caller closes
     * Room and installs it. Staging beside the live DB keeps the final move on
     * the same filesystem, which is required for an atomic rename.
     */
    suspend fun stageRestore(source: File): File = withContext(Dispatchers.IO) {
        requireValidDatabaseSnapshot(source)
        val live = liveDatabaseFile()
        live.parentFile?.mkdirs()
        val staged = File(live.parentFile, ".${live.name}.restore-${System.nanoTime()}.tmp")
        try {
            source.inputStream().use { input ->
                staged.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            requireValidDatabaseSnapshot(staged)
            staged
        } catch (t: Throwable) {
            staged.delete()
            throw t
        }
    }

    /**
     * Install a validated snapshot as the live Room database. The caller's UI
     * already enforces a process restart after restore; closing Room here makes
     * the file replacement explicit and prevents a live connection from
     * continuing against the old inode.
     */
    suspend fun restoreSnapshot(source: File) = withContext(Dispatchers.IO) {
        val staged = stageRestore(source)
        val live = liveDatabaseFile()
        val wal = File(live.path + "-wal")
        val shm = File(live.path + "-shm")
        try {
            database.close()
            Files.move(
                staged.toPath(),
                live.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            // Sidecars belong to the database image that was just replaced.
            // The restored compact snapshot is self-contained and Room will
            // create fresh WAL/SHM files after the mandatory process restart.
            wal.delete()
            shm.delete()
        } catch (t: Throwable) {
            staged.delete()
            throw t
        }
    }
}
