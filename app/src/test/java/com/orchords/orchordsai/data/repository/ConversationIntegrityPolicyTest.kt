package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.model.Conversation
import com.orchords.orchordsai.data.model.ConversationLoadState
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationIntegrityPolicyTest {
    @Test
    fun `partial conversation cannot enter destructive rewrite path`() {
        val partial = Conversation(
            assistantId = Uuid.random(),
            messageNodes = emptyList(),
            loadState = ConversationLoadState.PARTIAL,
            corruptNodeIds = setOf("corrupt-node"),
        )

        assertThrows(IllegalStateException::class.java) {
            requireCompleteConversationForRewrite(partial)
        }
    }
}
