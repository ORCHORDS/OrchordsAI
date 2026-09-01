package com.orchords.orchordsai.service

import com.orchords.orchordsai.data.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationSessionTest {
    private fun session(initial: Conversation): ConversationSession = ConversationSession(
        id = initial.id,
        initial = initial,
        scope = CoroutineScope(Dispatchers.Unconfined),
        onIdle = {},
    )

    @Test
    fun `late predecessor completion cannot clear current generation job`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val predecessor = Job()
        val current = Job()

        session.setJob(predecessor)
        session.setJob(current)
        predecessor.complete()

        assertSame(current, session.getJob())
        assertTrue(session.isGenerating)
        current.cancel()
    }

    @Test
    fun `storage hydration cannot replace a locally revised session`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val local = initial.copy(title = "newer local")
        val stale = initial.copy(title = "stale storage")

        session.update(local)

        assertFalse(session.replaceFromStorage(stale))
        assertEquals(local, session.state.value)
        assertEquals(1L, session.revision)
    }

    @Test
    fun `storage hydration cannot replace an actively generating session`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val activeGeneration = Job()
        val stale = initial.copy(title = "stale storage")

        session.setJob(activeGeneration)

        assertFalse(session.replaceFromStorage(stale))
        assertEquals(initial, session.state.value)
        assertFalse(session.hydrated)

        activeGeneration.cancel()
    }

    @Test
    fun `metadata mutation preserves authoritative message state`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val authoritative = initial.copy(title = "old", workspaceCwd = "/latest/workspace")
        session.update(authoritative)

        val (updated, revision) = session.mutate { it.copy(title = "new") }

        assertEquals("new", updated.title)
        assertEquals("/latest/workspace", updated.workspaceCwd)
        assertEquals(updated, session.state.value)
        assertEquals(2L, revision)
        assertFalse(session.replaceFromStorage(initial.copy(title = "stale")))
        assertEquals(updated, session.state.value)
    }

    @Test
    fun `first storage hydration establishes persisted baseline`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val stored = initial.copy(title = "stored")

        assertTrue(session.replaceFromStorage(stored))
        assertEquals(stored, session.state.value)
        assertTrue(session.hydrated)
        assertEquals(0L, session.persistedRevision)
    }

    @Test
    fun `late checkpoint completion cannot regress persisted revision`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)

        val firstRevision = session.update(initial.copy(title = "partial"))
        val finalRevision = session.update(initial.copy(title = "final"))

        session.markPersisted(finalRevision)
        session.markPersisted(firstRevision)

        assertEquals(finalRevision, session.persistedRevision)
        assertEquals("final", session.state.value.title)
    }

    @Test
    fun `process recreation can hydrate the last persisted partial snapshot`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val liveSession = session(initial)
        val partialSnapshot = initial.copy(title = "partial checkpoint", workspaceCwd = "/work/in-progress")
        val persistedRevision = liveSession.update(partialSnapshot)
        liveSession.markPersisted(persistedRevision)

        val recreatedSession = session(Conversation.ofId(initial.id, initial.assistantId))

        assertTrue(recreatedSession.replaceFromStorage(partialSnapshot))
        assertEquals(partialSnapshot, recreatedSession.state.value)
        assertTrue(recreatedSession.hydrated)
        assertEquals(0L, recreatedSession.persistedRevision)
    }
}
