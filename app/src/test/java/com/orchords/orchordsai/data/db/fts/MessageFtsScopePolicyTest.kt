package com.orchords.orchordsai.data.db.fts

import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFtsScopePolicyTest {
    @Test
    fun `assistant scoped search filters before ordering and limiting`() {
        val sql = messageFtsSearchSql(MessageSearchSort.RELEVANCE, assistantScoped = true)

        val scopeIndex = sql.indexOf("assistant_id = ?")
        val orderIndex = sql.indexOf("ORDER BY")
        val limitIndex = sql.indexOf("LIMIT 50")

        assertTrue(scopeIndex >= 0)
        assertTrue(scopeIndex < orderIndex)
        assertTrue(orderIndex < limitIndex)
    }
}
