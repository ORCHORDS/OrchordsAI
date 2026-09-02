package com.orchords.orchordsai.data.repository

import android.database.sqlite.SQLiteBlobTooBigException
import android.util.Log
import kotlinx.coroutines.CancellationException
import com.orchords.ai.ui.UIMessage
import com.orchords.orchordsai.data.db.dao.MessageNodeDAO
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.model.ConversationLoadState
import com.orchords.orchordsai.data.model.MessageNode
import com.orchords.orchordsai.utils.JsonInstant
import kotlin.uuid.Uuid

private const val TAG = "ConversationNodeLoader"
private const val NODE_PAGE_SIZE = 64

private fun logUnreadable(message: String) {
    // Diagnostics are best-effort and must never turn a recoverable corrupt row
    // into a loader failure (including JVM unit tests where android.util.Log is absent).
    runCatching { Log.w(TAG, message) }
}

internal data class MessageNodeLoadResult(
    val nodes: List<MessageNode>,
    val corruptNodeIds: Set<String>,
) {
    val loadState: ConversationLoadState
        get() = if (corruptNodeIds.isEmpty()) ConversationLoadState.COMPLETE else ConversationLoadState.PARTIAL
}

internal interface ConversationNodeReader {
    suspend fun count(conversationId: String): Int
    suspend fun page(conversationId: String, limit: Int, offset: Int): List<MessageNodeEntity>
    suspend fun nodeIdAtOffset(conversationId: String, offset: Int): String?
    suspend fun nodeById(nodeId: String): MessageNodeEntity?
}

internal suspend fun loadConversationNodesSafely(
    messageNodeDAO: MessageNodeDAO,
    conversationId: String,
    favoriteNodeIds: Set<Uuid>,
): MessageNodeLoadResult = loadConversationNodesSafely(
    reader = object : ConversationNodeReader {
        override suspend fun count(conversationId: String) = messageNodeDAO.countNodesOfConversation(conversationId)
        override suspend fun page(conversationId: String, limit: Int, offset: Int) =
            messageNodeDAO.getNodesOfConversationPaged(conversationId, limit, offset)
        override suspend fun nodeIdAtOffset(conversationId: String, offset: Int) =
            messageNodeDAO.getNodeIdAtOffset(conversationId, offset)
        override suspend fun nodeById(nodeId: String) = messageNodeDAO.getNodeById(nodeId)
    },
    conversationId = conversationId,
    favoriteNodeIds = favoriteNodeIds,
)

internal suspend fun loadConversationNodesSafely(
    reader: ConversationNodeReader,
    conversationId: String,
    favoriteNodeIds: Set<Uuid>,
): MessageNodeLoadResult {
    val nodes = mutableListOf<MessageNode>()
    val corruptNodeIds = linkedSetOf<String>()
    val total = reader.count(conversationId)
    var offset = 0

    fun decode(entity: MessageNodeEntity) {
        try {
            val nodeId = Uuid.parse(entity.id)
            nodes += MessageNode(
                id = nodeId,
                messages = JsonInstant.decodeFromString<List<UIMessage>>(entity.messages),
                selectIndex = entity.selectIndex,
                isFavorite = favoriteNodeIds.contains(nodeId),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            corruptNodeIds += entity.id
            logUnreadable(
                "Unreadable message node conversation=$conversationId node=${entity.id} error=${error.javaClass.simpleName}"
            )
        }
    }

    while (offset < total) {
        val page = try {
            reader.page(conversationId, NODE_PAGE_SIZE, offset)
        } catch (error: SQLiteBlobTooBigException) {
            null
        } catch (error: IllegalStateException) {
            null
        }

        if (page != null) {
            if (page.isEmpty()) break
            page.forEach(::decode)
            offset += page.size
            continue
        }

        val pageEnd = minOf(offset + NODE_PAGE_SIZE, total)
        for (rowOffset in offset until pageEnd) {
            val nodeId = reader.nodeIdAtOffset(conversationId, rowOffset) ?: continue
            val entity = try {
                reader.nodeById(nodeId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                corruptNodeIds += nodeId
                logUnreadable(
                    "Unreadable message row conversation=$conversationId node=$nodeId error=${error.javaClass.simpleName}"
                )
                null
            }
            if (entity != null) decode(entity)
        }
        offset = pageEnd
    }

    return MessageNodeLoadResult(nodes = nodes, corruptNodeIds = corruptNodeIds)
}
