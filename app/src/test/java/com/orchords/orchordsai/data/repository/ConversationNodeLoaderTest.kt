package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.model.ConversationLoadState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationNodeLoaderTest {
    @Test
    fun `failed 64 row page isolates one unreadable row without losing healthy neighbors`() = runBlocking {
        val rows = (0 until 70).map(::row)
        val badId = rows[10].id
        val reader = FakeReader(rows, failedPages = setOf(0), unreadableIds = setOf(badId))

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(69, result.nodes.size)
        assertEquals(setOf(badId), result.corruptNodeIds)
        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertEquals(rows.filterNot { it.id == badId }.map { it.id }, result.nodes.map { it.id.toString() })
        assertEquals((0 until 64).toList(), reader.singleRowOffsets)
    }

    @Test
    fun `two unreadable rows are isolated independently across failed pages`() = runBlocking {
        val rows = (0 until 150).map(::row)
        val badIds = setOf(rows[10].id, rows[130].id)
        val reader = FakeReader(rows, failedPages = setOf(0, 128), unreadableIds = badIds)

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(148, result.nodes.size)
        assertEquals(badIds, result.corruptNodeIds)
        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertTrue(result.nodes.none { it.id.toString() in badIds })
        assertEquals(rows.filterNot { it.id in badIds }.map { it.id }, result.nodes.map { it.id.toString() })
    }

    @Test
    fun `healthy pages retain fast paged path`() = runBlocking {
        val rows = (0 until 150).map(::row)
        val reader = FakeReader(rows)

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(150, result.nodes.size)
        assertEquals(emptySet<String>(), result.corruptNodeIds)
        assertEquals(ConversationLoadState.COMPLETE, result.loadState)
        assertEquals(listOf(0, 64, 128), reader.pageOffsets)
        assertTrue(reader.singleRowOffsets.isEmpty())
    }

    @Test
    fun `externalized payload resolves via the payload source`() = runBlocking {
        // Use the inline JSON format that the loader is wired to (same as the
        // happy-path inline rows: `"[]"` decodes cleanly).
        val blobbedJson = "[]"
        val rows = listOf(
            MessageNodeEntity(
                id = "00000000-0000-0000-0000-000000000001",
                conversationId = CONVERSATION_ID,
                nodeIndex = 0,
                messages = "",
                selectIndex = 0,
                payloadBlobId = 99L,
            ),
            row(1),
        )
        val reader = FakeReader(rows)
        val source = MapPayloadSource(mapOf(99L to blobbedJson))

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = source,
        )

        assertEquals(2, result.nodes.size)
        assertTrue(result.corruptNodeIds.isEmpty())
        assertEquals(ConversationLoadState.COMPLETE, result.loadState)
    }

    @Test
    fun `unreadable blob id marks the row corrupt but keeps neighbors`() = runBlocking {
        val rows = (0 until 3).map(::row)
        val broken = rows[1].copy(payloadBlobId = 42L)
        val rowsWithBlob = listOf(rows[0], broken, rows[2])
        val reader = FakeReader(rowsWithBlob)

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = MapPayloadSource(emptyMap()),
        )

        assertEquals(2, result.nodes.size)
        assertEquals(setOf(broken.id), result.corruptNodeIds)
        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertEquals(listOf(rows[0].id, rows[2].id), result.nodes.map { it.id.toString() })
    }

    private fun row(index: Int) = MessageNodeEntity(
        id = id(index),
        conversationId = CONVERSATION_ID,
        nodeIndex = index,
        messages = "[]",
        selectIndex = 0,
    )

    private fun id(index: Int): String = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    private class FakeReader(
        private val rows: List<MessageNodeEntity>,
        private val failedPages: Set<Int> = emptySet(),
        private val unreadableIds: Set<String> = emptySet(),
    ) : ConversationNodeReader {
        val pageOffsets = mutableListOf<Int>()
        val singleRowOffsets = mutableListOf<Int>()

        override suspend fun count(conversationId: String): Int = rows.size

        override suspend fun page(conversationId: String, limit: Int, offset: Int): List<MessageNodeEntity> {
            pageOffsets += offset
            if (offset in failedPages) throw IllegalStateException("simulated page read failure")
            return rows.drop(offset).take(limit)
        }

        override suspend fun nodeIdAtOffset(conversationId: String, offset: Int): String? {
            singleRowOffsets += offset
            return rows.getOrNull(offset)?.id
        }

        override suspend fun nodeById(nodeId: String): MessageNodeEntity? {
            if (nodeId in unreadableIds) throw IllegalStateException("simulated row read failure")
            return rows.firstOrNull { it.id == nodeId }
        }
    }

    companion object {
        private const val CONVERSATION_ID = "00000000-0000-0000-0000-000000000255"
    }
}

private object InlinePayloadSource : ConversationNodePayloadSource {
    override suspend fun resolve(entity: MessageNodeEntity): String? =
        entity.payloadBlobId?.let { null } ?: entity.messages.takeIf { it.isNotEmpty() }
}

private class MapPayloadSource(private val blobs: Map<Long, String>) : ConversationNodePayloadSource {
    override suspend fun resolve(entity: MessageNodeEntity): String? {
        val blobId = entity.payloadBlobId ?: return entity.messages.takeIf { it.isNotEmpty() }
        return blobs[blobId]
    }
}
