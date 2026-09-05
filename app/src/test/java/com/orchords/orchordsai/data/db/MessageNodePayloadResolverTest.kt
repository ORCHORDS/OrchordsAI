package com.orchords.orchordsai.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNodePayloadResolverTest {

    @Test
    fun `empty payload does not externalize`() {
        assertFalse(MessageNodePayloadResolver.shouldExternalize(""))
    }

    @Test
    fun `payload one byte under threshold does not externalize`() {
        val json = "[" + "x".repeat(MessageNodePayloadStore.MAX_INLINE_BYTES - 2) + "]"
        assertEquals((MessageNodePayloadStore.MAX_INLINE_BYTES).toLong(), json.toByteArray(Charsets.UTF_8).size.toLong())
        assertFalse(MessageNodePayloadResolver.shouldExternalize(json))
    }

    @Test
    fun `payload at exactly threshold does not externalize`() {
        val padding = "x".repeat(MessageNodePayloadStore.MAX_INLINE_BYTES - 2)
        val json = "[" + padding + "]"
        assertEquals(MessageNodePayloadStore.MAX_INLINE_BYTES.toLong(), json.toByteArray(Charsets.UTF_8).size.toLong())
        assertFalse(MessageNodePayloadResolver.shouldExternalize(json))
    }

    @Test
    fun `payload one byte over threshold externalizes`() {
        val padding = "x".repeat(MessageNodePayloadStore.MAX_INLINE_BYTES - 2 + 1)
        val json = "[" + padding + "]"
        assertEquals((MessageNodePayloadStore.MAX_INLINE_BYTES + 1).toLong(), json.toByteArray(Charsets.UTF_8).size.toLong())
        assertTrue(MessageNodePayloadResolver.shouldExternalize(json))
    }

    @Test
    fun `payload at 300 KiB externalizes`() {
        val json = buildString { repeat(300 * 1024) { append('x') } }
        assertTrue(json.length > MessageNodePayloadStore.MAX_INLINE_BYTES)
        assertTrue(MessageNodePayloadResolver.shouldExternalize(json))
    }

    @Test
    fun `payload at 1 MiB externalizes`() {
        val json = buildString { repeat(1024 * 1024) { append('x') } }
        assertTrue(MessageNodePayloadResolver.shouldExternalize(json))
    }

    @Test
    fun `multi byte UTF 8 characters are counted by byte length`() {
        val emoji = "\uD83D\uDE00" // 4 bytes in UTF-8
        assertEquals(4, emoji.toByteArray(Charsets.UTF_8).size)
        // (MAX / 4) emojis = exactly MAX bytes -> not externalized; one more emoji pushes us over.
        val atThreshold = buildString { repeat(MessageNodePayloadStore.MAX_INLINE_BYTES / 4) { append(emoji) } }
        assertEquals(
            MessageNodePayloadStore.MAX_INLINE_BYTES.toLong(),
            atThreshold.toByteArray(Charsets.UTF_8).size.toLong(),
        )
        assertFalse(MessageNodePayloadResolver.shouldExternalize(atThreshold))
        val overThreshold = atThreshold + emoji
        assertTrue(MessageNodePayloadResolver.shouldExternalize(overThreshold))
    }
}
