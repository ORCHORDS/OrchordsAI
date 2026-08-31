package com.orchords.orchordsai.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentMimeClassifierTest {
    @Test
    fun `typescript source overrides ambiguous Android transport stream MIME`() {
        val source = """
            export interface User { id: string }
            export const user: User = { id: "fixture" }
        """.trimIndent().toByteArray()

        assertEquals(
            "text/plain",
            resolveAmbiguousTsMime("model.ts", "video/MP2T", source),
        )
        assertEquals(
            "text/plain",
            resolveAmbiguousTsMime("model.ts", "video/mp2ts", source),
        )
    }

    @Test
    fun `real MPEG transport stream keeps video MIME`() {
        val transportStream = ByteArray(188 * 3)
        transportStream[0] = 0x47.toByte()
        transportStream[188] = 0x47.toByte()
        transportStream[376] = 0x47.toByte()

        assertEquals(
            "video/mp2ts",
            resolveAmbiguousTsMime("capture.ts", "video/mp2ts", transportStream),
        )
    }

    @Test
    fun `192 byte transport packets with four byte prefix are recognized`() {
        val transportStream = ByteArray(4 + 192 * 3)
        transportStream[4] = 0x47.toByte()
        transportStream[196] = 0x47.toByte()
        transportStream[388] = 0x47.toByte()

        assertEquals("video/mp2t", classifyTsSample(transportStream))
    }

    @Test
    fun `non ambiguous MIME is untouched`() {
        assertEquals(
            "image/png",
            resolveAmbiguousTsMime("model.ts", "image/png", "export const x = 1".toByteArray()),
        )
        assertEquals(
            "video/mp2t",
            resolveAmbiguousTsMime("capture.m2ts", "video/mp2t", ByteArray(0)),
        )
    }
}
