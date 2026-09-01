package com.orchords.orchordsai.data.sync

import android.content.Context
import com.orchords.orchordsai.data.db.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val APP_DATABASE_NAME = "orchordsai.db"
const val DATABASE_BACKUP_ENTRY = "orchordsai.db"

private val SQLITE_FILE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

internal fun hasSQLiteFileHeader(file: File): Boolean {
    if (!file.isFile || file.length() < SQLITE_FILE_HEADER.size) return false
    val actual = ByteArray(SQLITE_FILE_HEADER.size)
    FileInputStream(file).use { input ->
        if (input.read(actual) != actual.size) return false
    }
    return actual.contentEquals(SQLITE_FILE_HEADER)
}

class DatabaseSnapshotService(
    private val context: Context,
    private val database: AppDatabase,
) {
    fun liveDatabaseFile(): File = context.getDatabasePath(APP_DATABASE_NAME)

    suspend fun createSnapshot(): File = withContext(Dispatchers.IO) {
        val snapshot = File.createTempFile("orchordsai-db-snapshot-", ".db", context.cacheDir)
        try {
            database.openHelper.writableDatabase.execSQL(
                "VACUUM INTO ?",
                arrayOf(snapshot.absolutePath),
            )
            check(hasSQLiteFileHeader(snapshot)) {
                "SQLite snapshot was not created correctly"
            }
            snapshot
        } catch (error: Throwable) {
            snapshot.delete()
            throw error
        }
    }

    suspend fun stageRestore(source: File): File = withContext(Dispatchers.IO) {
        check(hasSQLiteFileHeader(source)) {
            "Backup database snapshot is missing or invalid"
        }
        val liveFile = liveDatabaseFile()
        val databaseDir = liveFile.parentFile
            ?: error("Database directory is unavailable")
        check(databaseDir.exists() || databaseDir.mkdirs()) {
            "Database directory could not be created"
        }
        val staged = File.createTempFile("orchordsai-db-restore-", ".db", databaseDir)
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(staged).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            check(hasSQLiteFileHeader(staged)) {
                "Staged database snapshot is invalid"
            }
            staged
        } catch (error: Throwable) {
            staged.delete()
            throw error
        }
    }

    suspend fun restoreSnapshot(source: File) = withContext(Dispatchers.IO) {
        val staged = stageRestore(source)
        val liveFile = liveDatabaseFile()
        val walFile = File(liveFile.path + "-wal")
        val shmFile = File(liveFile.path + "-shm")

        try {
            database.close()
            Files.move(
                staged.toPath(),
                liveFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            walFile.delete()
            shmFile.delete()
        } catch (error: Throwable) {
            staged.delete()
            throw error
        }
    }
}
