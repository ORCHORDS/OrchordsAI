package com.orchords.orchordsai.ui.pages.chat

data class ViewportFollowPolicy(
    val conversationId: String,
    val ownsFollow: Boolean = true,
    val imeHeight: Int = 0,
) {
    fun onReaderNavigation() = copy(ownsFollow = false)

    fun onReturnToLatest() = copy(ownsFollow = true)

    fun onSend() = copy(ownsFollow = true)

    fun onConversationChanged(newConversationId: String): ImeFollowUpdate =
        if (newConversationId == conversationId) {
            ImeFollowUpdate(this, 0)
        } else {
            ImeFollowUpdate(ViewportFollowPolicy(newConversationId), 0)
        }

    fun onImeHeightChanged(newHeight: Int): ImeFollowUpdate {
        val normalizedHeight = newHeight.coerceAtLeast(0)
        return ImeFollowUpdate(
            policy = copy(imeHeight = normalizedHeight),
            imeScrollDelta = if (ownsFollow) normalizedHeight - imeHeight else 0,
        )
    }
}

data class ImeFollowUpdate(
    val policy: ViewportFollowPolicy,
    val imeScrollDelta: Int,
)
