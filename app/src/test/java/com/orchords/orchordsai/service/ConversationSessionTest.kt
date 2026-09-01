package com.orchords.orchordsai.service

import com.orchords.orchordsai.data.model.Conversation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    @Test
    fun `structural mutation waits for cancellation flush of active generation`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val flushEntered = java.util.concurrent.atomic.AtomicBoolean(false)
        val flushDone = java.util.concurrent.atomic.AtomicBoolean(false)
        val mutatorFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        val job = scope.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    flushEntered.set(true)
                    // The cancellation flush is slow: a structural mutation must not observe
                    // session state until this block finishes.
                    while (!flushDone.get()) delay(10)
                    session.update(initial.copy(title = "flushed by cancelled attempt"))
                }
            }
        }
        session.setJob(job)

        val mutator = scope.launch {
            session.awaitInactiveGeneration()
            // The fence must return only after the cancelled attempt's flush completed.
            assertTrue(flushEntered.get())
            assertTrue(flushDone.get())
            session.update(initial.copy(title = "structural mutation"))
            mutatorFinished.set(true)
        }

        // Let the cancelled job reach its finally block, then release the flush while the
        // mutator is (still) suspended on the fence.
        while (!flushEntered.get()) Thread.sleep(10)
        Thread.sleep(100)
        assertFalse(mutatorFinished.get())
        assertTrue(job.isCancelled)

        flushDone.set(true)
        while (!mutatorFinished.get()) Thread.sleep(10)
        assertFalse(mutator.isCancelled)
        assertEquals("structural mutation", session.state.value.title)
    }

    @Test
    fun `cancellation flush can reacquire persistence mutex without deadlock`() = runBlocking {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val scope = CoroutineScope(Dispatchers.Default)
        val flushEntered = CompletableDeferred<Unit>()
        val flushMayProceed = CompletableDeferred<Unit>()
        val fenceReturned = CompletableDeferred<Unit>()

        val attemptJob = scope.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    flushEntered.complete(Unit)
                    flushMayProceed.await()
                    session.persistenceMutex.withLock {
                        session.update(initial.copy(title = "flushed through persistence mutex"))
                    }
                }
            }
        }
        session.setJob(attemptJob)

        val fenceJob = scope.launch {
            session.awaitInactiveGeneration()
            fenceReturned.complete(Unit)
        }

        flushEntered.await()
        delay(50)
        flushMayProceed.complete(Unit)

        val completed = withTimeoutOrNull(1_000) {
            fenceReturned.await()
            true
        } ?: false

        if (!completed) {
            fenceJob.cancelAndJoin()
        }

        assertTrue("Cancellation flush must not deadlock on persistenceMutex", completed)
        assertTrue(attemptJob.isCompleted)
        assertEquals("flushed through persistence mutex", session.state.value.title)
    }

    @Test
    fun `structural mutation after fence cannot be overwritten by late attempt state`() {
        val initial = Conversation(assistantId = Uuid.random(), messageNodes = emptyList())
        val session = session(initial)
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val attemptJob = scope.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    session.update(initial.copy(title = "late attempt write"))
                }
            }
        }
        session.setJob(attemptJob)

        val mutatorFinished = java.util.concurrent.atomic.AtomicBoolean(false)
        val mutator = scope.launch {
            session.awaitInactiveGeneration()
            session.update(initial.copy(title = "structural mutation"))
            mutatorFinished.set(true)
        }
        while (!mutatorFinished.get()) Thread.sleep(10)

        assertEquals("structural mutation", session.state.value.title)
        val structuralRevision = session.revision
        assertTrue(session.persistedRevision <= structuralRevision)
    }
}
