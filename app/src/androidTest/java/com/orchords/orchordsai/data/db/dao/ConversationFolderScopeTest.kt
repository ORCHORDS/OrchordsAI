package com.orchords.orchordsai.data.db.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.repository.LightConversationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationFolderScopeTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun folderPagingRequiresMatchingAssistantAtQueryBoundary() = runBlocking {
        val dao = database.conversationDao()
        dao.insert(conversation(CONVERSATION_A, ASSISTANT_A, FOLDER_A, 1L))
        dao.insert(conversation(CONVERSATION_B, ASSISTANT_B, FOLDER_B, 2L))

        assertEquals(
            listOf(CONVERSATION_A),
            loadIds(dao.getConversationsOfFolderOfAssistantPaging(ASSISTANT_A, FOLDER_A)),
        )
        assertEquals(
            listOf(CONVERSATION_B),
            loadIds(dao.getConversationsOfFolderOfAssistantPaging(ASSISTANT_B, FOLDER_B)),
        )
        assertTrue(
            loadIds(dao.getConversationsOfFolderOfAssistantPaging(ASSISTANT_B, FOLDER_A)).isEmpty()
        )
        assertTrue(
            loadIds(dao.getConversationsOfFolderOfAssistantPaging(ASSISTANT_A, FOLDER_B)).isEmpty()
        )
    }

    private suspend fun loadIds(
        pagingSource: PagingSource<Int, LightConversationEntity>,
    ): List<String> = try {
        when (val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            )
        )) {
            is PagingSource.LoadResult.Page -> result.data.map { it.id }
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> emptyList()
        }
    } finally {
        pagingSource.invalidate()
    }

    private fun conversation(
        id: String,
        assistantId: String,
        folderId: String,
        updateAt: Long,
    ) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        title = id,
        nodes = "[]",
        createAt = updateAt,
        updateAt = updateAt,
        chatSuggestions = "[]",
        isPinned = false,
        folderId = folderId,
    )

    private companion object {
        const val ASSISTANT_A = "11111111-1111-1111-1111-111111111111"
        const val ASSISTANT_B = "22222222-2222-2222-2222-222222222222"
        const val FOLDER_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val FOLDER_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        const val CONVERSATION_A = "aaaaaaaa-1111-1111-1111-111111111111"
        const val CONVERSATION_B = "bbbbbbbb-2222-2222-2222-222222222222"
    }
}
