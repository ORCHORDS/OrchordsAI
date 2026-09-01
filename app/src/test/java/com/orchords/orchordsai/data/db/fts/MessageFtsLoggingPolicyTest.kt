package com.orchords.orchordsai.data.db.fts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MessageFtsLoggingPolicyTest {
    @Test
    fun `search log marker never contains query text`() {
        val query = "private recovery phrase"
        val marker = messageFtsSearchLogMarker()

        assertEquals("search executed", marker)
        assertFalse(marker.contains(query))
    }
}
