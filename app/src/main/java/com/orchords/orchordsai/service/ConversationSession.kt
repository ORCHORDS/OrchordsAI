package com.orchords.orchordsai.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.orchords.orchordsai.data.model.Conversation
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.Uuid

private const val TAG = "ConversationSession"
private const val IDLE_TIMEOUT_MS = 5_000L

class ConversationSession(
    val id: Uuid,
    initial: Conversation,
    private val scope: CoroutineScope,
    private val onIdle: (Uuid) -> Unit,
) {
    val state = MutableStateFlow(initial)

    /** Serializes hydration and durable writes for this conversation. */
    val persistenceMutex = Mutex()
    private val revisionCounter = AtomicLong(0)
    @Volatile var hydrated: Boolean = false
        private set
    @Volatile var persistedRevision: Long = -1
        private set

    val revision: Long get() = revisionCounter.get()

    @Synchronized
    fun replaceFromStorage(conversation: Conversation): Boolean {
        if (hydrated || isGenerating || revision != 0L) return false
        state.value = conversation
        hydrated = true
        persistedRevision = revision
        return true
    }

    @Synchronized
    fun markNewConversationHydrated(conversation: Conversation) {
        if (!hydrated && !isGenerating && revision == 0L) {
            state.value = conversation
            hydrated = true
            persistedRevision = revision
        }
    }

    @Synchronized
    fun update(conversation: Conversation): Long {
        state.value = conversation
        hydrated = true
        return revisionCounter.incrementAndGet()
    }

    @Synchronized
    fun mutate(transform: (Conversation) -> Conversation): Pair<Conversation, Long> {
        val updated = transform(state.value)
        state.value = updated
        hydrated = true
        return updated to revisionCounter.incrementAndGet()
    }

    @Synchronized
    fun markPersisted(revision: Long) {
        if (revision > persistedRevision) persistedRevision = revision
    }

    /**
     * Cancels any active generation and suspends until its cancellation flush has fully
     * completed, so a structural mutation (edit/delete/branch select) never races streamed
     * writes from the previous attempt. Runs under the persistence mutex so checkpoint
     * writes issued by the dying job are ordered before whatever the caller saves next.
     */
    suspend fun awaitInactiveGeneration() {
        val job = _generationJob.value ?: return
        job.cancel()
        persistenceMutex.withLock {
            job.join()
        }
    }

    private val refCount = AtomicInteger(0)

    val processingStatus = MutableStateFlow<String?>(null)

    private val _generationJob = MutableStateFlow<Job?>(null)
    val generationJob: StateFlow<Job?> = _generationJob.asStateFlow()
    val isGenerating: Boolean get() = _generationJob.value?.isActive == true
    val isInUse: Boolean get() = refCount.get() > 0 || isGenerating

    private var idleCheckJob: Job? = null

    fun acquire(): Int = refCount.incrementAndGet().also {
        cancelIdleCheck()
        Log.d(TAG, "acquire $id (refs=$it)")
    }

    fun release(): Int = refCount.decrementAndGet().also {
        Log.d(TAG, "release $id (refs=$it)")
        if (it <= 0) scheduleIdleCheck()
    }

    inline fun <T> withRef(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    suspend inline fun <T> withRefSuspend(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    fun setJob(job: Job?) {
        _generationJob.value?.cancel()
        _generationJob.value = job
        job?.invokeOnCompletion {
            // A cancelled predecessor must not clear a newer generation's identity.
            if (_generationJob.compareAndSet(job, null) && refCount.get() <= 0) {
                scheduleIdleCheck()
            }
        }
    }

    fun getJob(): Job? = _generationJob.value

    private fun scheduleIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (refCount.get() <= 0 && !isGenerating) {
                onIdle(id)
            }
        }
    }

    private fun cancelIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = null
    }

    fun cleanup() {
        _generationJob.value?.cancel()
        _generationJob.value = null
        idleCheckJob?.cancel()
        idleCheckJob = null
    }
}
