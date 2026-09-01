package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.model.Conversation

internal fun requireCompleteConversationForRewrite(conversation: Conversation) {
    check(!conversation.hasIntegrityIssue) {
        "Conversation contains unreadable message nodes and cannot be rewritten safely"
    }
}
