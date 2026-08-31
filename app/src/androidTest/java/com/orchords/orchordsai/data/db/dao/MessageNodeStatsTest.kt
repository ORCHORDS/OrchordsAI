package com.orchords.orchordsai.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageNodeStatsTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MessageNodeDAO

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.messageNodeDao()
        database.conversationDao().insert(
            ConversationEntity(
                id = CONVERSATION_ID,
                assistantId = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e",
                title = "fixture",
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
    fun mixedValidMalformedAndEmptyRowsKeepStatisticsAvailable() = runBlocking {
        dao.insertAll(
            listOf(
                node(
                    id = "valid-1",
                    index = 0,
                    messages = """[{"role":"user","createdAt":"2026-08-31T10:00:00","usage":{"promptTokens":10,"completionTokens":2,"cachedTokens":1}}]""",
                ),
                node(
                    id = "malformed",
                    index = 1,
                    messages = "[{broken",
                ),
                node(
                    id = "empty",
                    index = 2,
                    messages = "[]",
                ),
                node(
                    id = "valid-2",
                    index = 3,
                    messages = """[{"role":"user","createdAt":"2026-09-01T11:00:00","usage":{"promptTokens":5,"completionTokens":7,"cachedTokens":0}}]""",
                ),
            )
        )

        val tokenStats = dao.getTokenStats()
        assertEquals(2, tokenStats.totalMessages)
        assertEquals(15L, tokenStats.promptTokens)
        assertEquals(9L, tokenStats.completionTokens)
        assertEquals(1L, tokenStats.cachedTokens)
        assertEquals(1, dao.getInvalidMessageJsonCount())

        val countsByDay = dao.getMessageCountPerDay("2026-08-01").associate { it.day to it.count }
        assertEquals(mapOf("2026-08-31" to 1, "2026-09-01" to 1), countsByDay)
    }

    private fun node(id: String, index: Int, messages: String) = MessageNodeEntity(
        id = id,
        conversationId = CONVERSATION_ID,
        nodeIndex = index,
        messages = messages,
        selectIndex = 0,
    )

    companion object {
        private const val CONVERSATION_ID = "00000000-0000-0000-0000-000000000059"
    }
}
