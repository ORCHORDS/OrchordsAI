package com.orchords.orchordsai.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import com.orchords.orchordsai.data.model.ConversationLoadState

class ConversationPagedLoadTest {
    private class PageFailure : IllegalStateException("synthetic unreadable row")

    @Test
    fun `one unreadable row does not discard healthy neighbors`() = runBlocking {
        val source = (0 until 150).toList()
        val badOffsets = setOf(10)

        val result = loadPagedIsolatingFailures(
            totalCount = source.size,
            pageSize = 64,
            isRecoverablePageFailure = { it is PageFailure },
        ) { offset, limit ->
            val end = (offset + limit).coerceAtMost(source.size)
            if ((offset until end).any { it in badOffsets }) throw PageFailure()
            source.subList(offset, end)
        }

        assertEquals(source.filterNot { it in badOffsets }, result.items)
        assertEquals(badOffsets, result.failedOffsets)
    }

    @Test
    fun `multiple failures including page boundaries are isolated independently`() = runBlocking {
        val source = (0 until 150).toList()
        val badOffsets = setOf(63, 64, 149)

        val result = loadPagedIsolatingFailures(
            totalCount = source.size,
            pageSize = 64,
            isRecoverablePageFailure = { it is PageFailure },
        ) { offset, limit ->
            val end = (offset + limit).coerceAtMost(source.size)
            if ((offset until end).any { it in badOffsets }) throw PageFailure()
            source.subList(offset, end)
        }

        assertEquals(source.filterNot { it in badOffsets }, result.items)
        assertEquals(badOffsets, result.failedOffsets)
    }

    @Test
    fun `healthy data retains page sized fast path`() = runBlocking {
        val source = (0 until 150).toList()
        val calls = mutableListOf<Pair<Int, Int>>()

        val result = loadPagedIsolatingFailures(
            totalCount = source.size,
            pageSize = 64,
            isRecoverablePageFailure = { false },
        ) { offset, limit ->
            calls += offset to limit
            source.subList(offset, offset + limit)
        }

        assertEquals(source, result.items)
        assertEquals(emptySet<Int>(), result.failedOffsets)
        assertEquals(listOf(0 to 64, 64 to 64, 128 to 22), calls)
    }

    @Test
    fun `partial conversation cannot be destructively rewritten`() {
        assertThrows(IllegalStateException::class.java) {
            requireCompleteConversationForRewrite(ConversationLoadState.PARTIAL)
        }
        requireCompleteConversationForRewrite(ConversationLoadState.COMPLETE)
    }
}
