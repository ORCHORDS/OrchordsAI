package com.orchords.orchordsai.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.MessageNodePayloadResolver
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.model.ConversationLoadState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationNodeLoaderRoomTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.conversationDao().insert(
            ConversationEntity(
                id = CONVERSATION_ID,
                assistantId = ASSISTANT_ID,
                title = "loader fixture",
                nodes = "[]",
                createAt = 0L,
                updateAt = 0L,
                chatSuggestions = "[]",
                isPinned = false,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun malformedRowIsIsolatedWithoutDeletingHealthyPersistedNeighbors() = runBlocking {
        val rows = insertRows(corruptIndices = setOf(64))

        val result = loadConversationNodesSafely(
            messageNodeDAO = database.messageNodeDao(),
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertPartialRowsPreserved(rows, corruptIndices = setOf(64), result = result)
    }

    @Test
    fun malformedRowsAcrossPageBoundaryAreIsolatedWithoutDeletingPersistedRows() = runBlocking {
        val corruptIndices = setOf(63, 64, 129)
        val rows = insertRows(corruptIndices)

        val result = loadConversationNodesSafely(
            messageNodeDAO = database.messageNodeDao(),
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertPartialRowsPreserved(rows, corruptIndices, result)
    }

    @Test
    fun healthyRoomConversationRemainsCompleteAndOrdered() = runBlocking {
        val rows = insertRows(corruptIndices = emptySet())

        val result = loadConversationNodesSafely(
            messageNodeDAO = database.messageNodeDao(),
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(ConversationLoadState.COMPLETE, result.loadState)
        assertTrue(result.corruptNodeIds.isEmpty())
        assertEquals(rows.map { it.id }, result.nodes.map { it.id.toString() })
        assertEquals(rows.size, database.messageNodeDao().countNodesOfConversation(CONVERSATION_ID))
    }

    /**
     * Sanity check that the resolver agrees a 300 KiB JSON would be externalized,
     * and that the same JSON decodes round-trip through kotlinx.serialization.
     * The full externalization path lives in the integration test
     * [MessageNodePayloadStoreTest] (and via FilesManager in androidTest).
     */
    @Test
    fun oversizedMessageJsonExceedsResolverThreshold() {
        val padding = "x".repeat(MessageNodePayloadResolverThreshold() + 1)
        val oversized = buildMessageJson(padding)
        assertTrue(MessageNodePayloadResolver.shouldExternalize(oversized))
        assertTrue(MessageNodePayloadResolver.shouldExternalize("[]").not())
    }

    private suspend fun insertRows(corruptIndices: Set<Int>): List<MessageNodeEntity> {
        val rows = (0 until ROW_COUNT).map { index ->
            MessageNodeEntity(
                id = nodeId(index),
                conversationId = CONVERSATION_ID,
                nodeIndex = index,
                messages = if (index in corruptIndices) "[{broken" else "[]",
                selectIndex = 0,
            )
        }
        database.messageNodeDao().insertAll(rows)
        return rows
    }

    private suspend fun assertPartialRowsPreserved(
        rows: List<MessageNodeEntity>,
        corruptIndices: Set<Int>,
        result: MessageNodeLoadResult,
    ) {
        val corruptIds: Set<String> = corruptIndices.mapTo(LinkedHashSet()) { nodeId(it) }
        val dao = database.messageNodeDao()

        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertEquals(corruptIds, result.corruptNodeIds)
        assertEquals(rows.size - corruptIndices.size, result.nodes.size)
        assertEquals(
            rows.filterNot { it.nodeIndex in corruptIndices }.map { it.id },
            result.nodes.map { it.id.toString() },
        )
        assertEquals(rows.size, dao.countNodesOfConversation(CONVERSATION_ID))
        for (corruptId in corruptIds) {
            val stillThere = dao.getNodeById(corruptId)
            assertNotNull("Corrupt row $corruptId should survive in the DB", stillThere)
        }
    }

    private fun nodeId(index: Int): String =
        "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    private fun MessageNodePayloadResolverThreshold(): Int = 256 * 1024

    private fun buildMessageJson(padding: String): String =
        "[" +
            "{\"role\":\"user\",\"createdAt\":1,\"parts\":[{\"text\":\"${padding}\"}]}" +
            "]"

    companion object {
        private const val ROW_COUNT = 150
        private const val CONVERSATION_ID = "00000000-0000-0000-0000-000000000255"
        private const val ASSISTANT_ID = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e"
    }
}

private object InlinePayloadSource : ConversationNodePayloadSource {
    override suspend fun resolve(entity: MessageNodeEntity): String? =
        entity.payloadBlobId?.let { null } ?: entity.messages.takeIf { it.isNotEmpty() }
}
