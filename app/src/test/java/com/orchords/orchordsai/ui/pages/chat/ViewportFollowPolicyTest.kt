package com.orchords.orchordsai.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportFollowPolicyTest {
    @Test
    fun `explicit reader navigation revokes follow ownership`() {
        val policy = ViewportFollowPolicy(conversationId = "one")

        val navigated = policy.onReaderNavigation()

        assertFalse(navigated.ownsFollow)
    }

    @Test
    fun `return to latest and send restore follow ownership`() {
        val detached = ViewportFollowPolicy("one").onReaderNavigation()

        assertTrue(detached.onReturnToLatest().ownsFollow)
        assertTrue(detached.onSend().ownsFollow)
    }

    @Test
    fun `conversation identity change resets follow ownership and ime baseline`() {
        val detached = ViewportFollowPolicy("one", ownsFollow = false, imeHeight = 480)

        val changed = detached.onConversationChanged("two")

        assertTrue(changed.policy.ownsFollow)
        assertEquals(0, changed.policy.imeHeight)
        assertEquals(0, changed.imeScrollDelta)
    }

    @Test
    fun `ime growth scrolls only while follow ownership is active`() {
        val owned = ViewportFollowPolicy("one", imeHeight = 100)
        val detached = owned.onReaderNavigation()

        assertEquals(140, owned.onImeHeightChanged(240).imeScrollDelta)
        assertEquals(0, detached.onImeHeightChanged(240).imeScrollDelta)
    }

    @Test
    fun `ime zero and shrink update baseline`() {
        val initial = ViewportFollowPolicy("one", imeHeight = 400)
        val shrunk = initial.onImeHeightChanged(150)
        val hidden = shrunk.policy.onImeHeightChanged(0)
        val reopened = hidden.policy.onImeHeightChanged(90)

        assertEquals(-250, shrunk.imeScrollDelta)
        assertEquals(-150, hidden.imeScrollDelta)
        assertEquals(90, reopened.imeScrollDelta)
        assertEquals(90, reopened.policy.imeHeight)
    }
}
