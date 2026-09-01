package com.orchords.orchordsai.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orchords.orchordsai.data.db.APP_DATABASE_SCHEMA_VERSION
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.FolderEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSnapshotIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun walCommittedGraphRoundTripsThroughSnapshotRestore() = runBlocking {
        var database = openDatabase()
        var snapshot: File? = null
        try {
            disableWalAutoCheckpoint(database)
            val folder = FolderEntity(
                id = "folder-1",
                assistantId = "assistant-1",
                name = "Snapshot folder",
                sortIndex = 2,
                createAt = 1_000L,
            )
            val conversation = ConversationEntity(
                id = "conversation-1",
                assistantId = "assistant-1",
                title = "Snapshot conversation",
                nodes = "[]",
                createAt = 2_000L,
                updateAt = 3_000L,
                chatSuggestions = "[]",
                isPinned = true,
                folderId = folder.id,
            )
            val messages = """[{"role":"user","createdAt":"2026-09-01T00:00:00Z","text":"wal-visible"}]"""
            val node = MessageNodeEntity(
                id = "node-1",
                conversationId = conversation.id,
                nodeIndex = 0,
                messages = messages,
                selectIndex = 0,
            )

            database.folderDao().insert(folder)
            database.conversationDao().insert(conversation)
            database.messageNodeDao().insert(node)

            val wal = File(context.getDatabasePath(APP_DATABASE_NAME).path + "-wal")
            assertTrue("fixture must retain committed state in WAL", wal.isFile && wal.length() > 0L)

            val service = DatabaseSnapshotService(context, database)
            val createdSnapshot = service.createSnapshot()
            snapshot = createdSnapshot
            assertTrue(isValidDatabaseSnapshot(createdSnapshot))

            database.messageNodeDao().deleteByConversation(conversation.id)
            database.conversationDao().deleteById(conversation.id)
            database.folderDao().deleteById(folder.id)

            service.restoreSnapshot(createdSnapshot)
            database = openDatabase()

            assertEquals(folder, database.folderDao().getFolderById(folder.id))
            assertEquals(conversation, database.conversationDao().getConversationById(conversation.id))
            val restoredNodes = database.messageNodeDao().getNodesOfConversation(conversation.id)
            assertEquals(1, restoredNodes.size)
            assertEquals(node, restoredNodes.single())
        } finally {
            database.close()
            snapshot?.delete()
        }
    }

    @Test
    fun vacuumSnapshotPurgesDeletedPayloadAndReclaimsFreelistSpace() = runBlocking {
        val database = openDatabase()
        var snapshot: File? = null
        try {
            disableWalAutoCheckpoint(database)
            val conversation = ConversationEntity(
                id = "bulk-conversation",
                assistantId = "assistant-1",
                title = "Bulk fixture",
                nodes = "[]",
                createAt = 4_000L,
                updateAt = 5_000L,
                chatSuggestions = "[]",
                isPinned = false,
            )
            database.conversationDao().insert(conversation)

            val marker = "ORCHORDS_DELETED_SNAPSHOT_MARKER"
            val payload = "x".repeat(16 * 1024)
            database.messageNodeDao().insertAll(
                (0 until 128).map { index ->
                    MessageNodeEntity(
                        id = "bulk-$index",
                        conversationId = conversation.id,
                        nodeIndex = index,
                        messages = """[{"marker":"$marker-$index-$payload"}]""",
                        selectIndex = 0,
                    )
                }
            )
            checkpoint(database)

            val live = context.getDatabasePath(APP_DATABASE_NAME)
            val populatedSize = live.length()
            assertTrue("fixture must materially grow the live database", populatedSize > 1_000_000L)

            database.messageNodeDao().deleteByConversation(conversation.id)
            checkpoint(database)

            val createdSnapshot = DatabaseSnapshotService(context, database).createSnapshot()
            snapshot = createdSnapshot
            assertTrue(isValidDatabaseSnapshot(createdSnapshot))
            assertTrue(
                "VACUUM snapshot should reclaim at least half of the deleted fixture footprint",
                createdSnapshot.length() * 2 < populatedSize
            )
            val snapshotBytes = createdSnapshot.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse("deleted marker must not survive in compact snapshot pages", snapshotBytes.contains(marker))
        } finally {
            database.close()
            snapshot?.delete()
        }
    }

    @Test
    fun incompatibleRestoreFailsBeforeClosingOrReplacingLiveDatabase() = runBlocking {
        val database = openDatabase()
        val incompatible = File.createTempFile("wrong-schema-", ".db", context.cacheDir)
        var validationSnapshot: File? = null
        try {
            val conversation = ConversationEntity(
                id = "live-conversation",
                assistantId = "assistant-1",
                title = "Keep me",
                nodes = "[]",
                createAt = 6_000L,
                updateAt = 7_000L,
                chatSuggestions = "[]",
                isPinned = false,
            )
            database.conversationDao().insert(conversation)

            SQLiteDatabase.openOrCreateDatabase(incompatible, null).use { sqlite ->
                sqlite.execSQL("PRAGMA user_version = ${APP_DATABASE_SCHEMA_VERSION - 1}")
                sqlite.execSQL("CREATE TABLE ConversationEntity(id TEXT PRIMARY KEY)")
                sqlite.execSQL("CREATE TABLE conversation_folder(id TEXT PRIMARY KEY)")
                sqlite.execSQL("CREATE TABLE message_node(id TEXT PRIMARY KEY)")
            }
            assertFalse(isValidDatabaseSnapshot(incompatible))

            var failed = false
            try {
                DatabaseSnapshotService(context, database).restoreSnapshot(incompatible)
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertTrue("schema mismatch must fail restore", failed)
            assertNotNull(database.conversationDao().getConversationById(conversation.id))

            val createdValidationSnapshot = DatabaseSnapshotService(context, database).createSnapshot()
            validationSnapshot = createdValidationSnapshot
            assertTrue(isValidDatabaseSnapshot(createdValidationSnapshot))
        } finally {
            database.close()
            incompatible.delete()
            validationSnapshot?.delete()
        }
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, APP_DATABASE_NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .allowMainThreadQueries()
            .build()

    private fun disableWalAutoCheckpoint(database: AppDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA wal_autocheckpoint = 0").use { cursor ->
            while (cursor.moveToNext()) {
                // Consume the pragma result so Android executes the query-style PRAGMA.
            }
        }
    }

    private fun checkpoint(database: AppDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            while (cursor.moveToNext()) {
                // Consume the pragma result so the checkpoint completes before measuring the DB file.
            }
        }
    }
}
