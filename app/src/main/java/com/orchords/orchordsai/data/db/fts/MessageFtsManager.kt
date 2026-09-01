package com.orchords.orchordsai.data.db.fts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.model.Conversation
import com.orchords.orchordsai.data.model.MessageNode
import java.time.Instant

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Instant,
    val snippet: String,
)

enum class MessageSearchSort(val orderBy: String) {
    RELEVANCE("rank, update_at DESC"),
    NEWEST_FIRST("update_at DESC, rank"),
    OLDEST_FIRST("update_at ASC, rank"),
}

private const val TAG = "MessageFtsManager"

internal fun messageFtsSearchSql(
    sort: MessageSearchSort,
    assistantScoped: Boolean,
): String {
    val assistantFilter = if (assistantScoped) {
        """
        AND conversation_id IN (
            SELECT id FROM conversationentity WHERE assistant_id = ?
        )
        """.trimIndent()
    } else {
        ""
    }

    return """
        SELECT node_id, message_id, conversation_id, title, update_at,
               simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
        FROM message_fts
        WHERE text MATCH jieba_query(?)
        $assistantFilter
        ORDER BY ${sort.orderBy}
        LIMIT 50
    """.trimIndent()
}

class MessageFtsManager(private val database: AppDatabase) {

    private val db get() = database.openHelper.writableDatabase

    suspend fun indexConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        val conversationId = conversation.id.toString()
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
        conversation.messageNodes.forEach { node ->
            node.messages.forEach { message ->
                val text = message.extractFtsText()
                if (text.isNotBlank()) {
                    db.execSQL(
                        "INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            text,
                            node.id.toString(),
                            message.id.toString(),
                            conversationId,
                            conversation.title,
                            conversation.updateAt.toEpochMilli().toString(),
                        )
                    )
                }
            }
        }
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM message_fts")
    }

    suspend fun search(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ): List<MessageSearchResult> = searchInternal(
        keyword = keyword,
        assistantId = null,
        sort = sort,
    )

    suspend fun searchForAssistant(
        keyword: String,
        assistantId: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ): List<MessageSearchResult> = searchInternal(
        keyword = keyword,
        assistantId = assistantId,
        sort = sort,
    )

    private suspend fun searchInternal(
        keyword: String,
        assistantId: String?,
        sort: MessageSearchSort,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MessageSearchResult>()
        val cursor = db.query(
            messageFtsSearchSql(sort, assistantScoped = assistantId != null),
            if (assistantId == null) arrayOf(keyword) else arrayOf(keyword, assistantId),
        )
        Log.i(TAG, messageFtsSearchLogMarker())
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    MessageSearchResult(
                        nodeId = it.getString(0),
                        messageId = it.getString(1),
                        conversationId = it.getString(2),
                        title = it.getString(3),
                        updateAt = Instant.ofEpochMilli(it.getLong(4)),
                        snippet = it.getString(5),
                    )
                )
            }
        }
        results
    }
}

private fun UIMessage.extractFtsText(): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .take(10_000)