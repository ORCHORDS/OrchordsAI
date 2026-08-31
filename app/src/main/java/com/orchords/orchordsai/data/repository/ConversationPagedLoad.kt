package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.model.ConversationLoadState

internal data class PagedLoadIsolationResult<T>(
    val items: List<T>,
    val failedOffsets: Set<Int>,
)

internal suspend fun <T> loadPagedIsolatingFailures(
    totalCount: Int,
    pageSize: Int,
    isRecoverablePageFailure: (Throwable) -> Boolean,
    loadPage: suspend (offset: Int, limit: Int) -> List<T>,
): PagedLoadIsolationResult<T> {
    require(totalCount >= 0) { "totalCount must be non-negative" }
    require(pageSize > 0) { "pageSize must be positive" }

    val items = mutableListOf<T>()
    val failedOffsets = linkedSetOf<Int>()

    suspend fun loadRange(offset: Int, limit: Int) {
        try {
            items += loadPage(offset, limit)
        } catch (error: Throwable) {
            if (!isRecoverablePageFailure(error)) throw error
            if (limit == 1) {
                failedOffsets += offset
                return
            }

            val leftSize = limit / 2
            val rightSize = limit - leftSize
            loadRange(offset, leftSize)
            loadRange(offset + leftSize, rightSize)
        }
    }

    var offset = 0
    while (offset < totalCount) {
        val limit = minOf(pageSize, totalCount - offset)
        loadRange(offset, limit)
        offset += limit
    }

    return PagedLoadIsolationResult(
        items = items,
        failedOffsets = failedOffsets,
    )
}

internal fun requireCompleteConversationForRewrite(loadState: ConversationLoadState) {
    check(loadState == ConversationLoadState.COMPLETE) {
        "Conversation history is incomplete and cannot be destructively rewritten until repaired"
    }
}
