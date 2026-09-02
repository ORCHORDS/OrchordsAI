package com.orchords.orchordsai.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.model.ConversationLoadState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        val dao = database.messageNodeDao()
        val rows = (0 until 150).map { index ->
            MessageNodeEntity(
                id = nodeId(index),
                conversationId = CONVERSATION_ID,
                nodeIndex = index,
                messages = if (index == BAD_INDEX) "[{broken" else "[]",
                selectIndex = 0,
            )
        }
        dao.insertAll(rows)

        val result = loadConversationNodesSafely(
            messageNodeDAO = dao,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
        )

        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertEquals(setOf(nodeId(BAD_INDEX)), result.corruptNodeIds)
        assertEquals(149, result.nodes.size)
        assertEquals(
            rows.filterNot { it.nodeIndex == BAD_INDEX }.map { it.id },
            result.nodes.map { it.id.toString() },
        )

        assertEquals(150, dao.countNodesOfConversation(CONVERSATION_ID))
        assertTrue(dao.getNodeById(nodeId(BAD_INDEX)) != null)
    }

    private fun nodeId(index: Int): String =
        "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    companion object {
        private const val CONVERSATION_ID = "00000000-0000-0000-0000-000000000255"
        private const val ASSISTANT_ID = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e"
        private const val BAD_INDEX = 64
    }
}
