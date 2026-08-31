package com.orchords.orchordsai.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWaveTest {
    @Test
    fun `reads mono 16 bit PCM wave`() {
        val samples = byteArrayOf(1, 2, 3, 4)
        val wave = PcmWave.read(pcmWave(samples, sampleRate = 1_000))

        assertEquals(1_000, wave.sampleRate)
        assertEquals(1, wave.channelCount)
        assertEquals(16, wave.bitsPerSample)
        assertEquals(2, wave.durationMillis)
        assertArrayEquals(samples, wave.samples)
    }

    @Test
    fun `rejects non PCM wave`() {
        val bytes = pcmWave(byteArrayOf(1, 2), sampleRate = 1_000).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(20, 3)
        }

        assertThrows(IllegalArgumentException::class.java) { PcmWave.read(bytes) }
    }

    @Test
    fun `rejects truncated chunks`() {
        val bytes = pcmWave(byteArrayOf(1, 2), sampleRate = 1_000).copyOf(43)

        assertThrows(IllegalArgumentException::class.java) { PcmWave.read(bytes) }
    }

    private fun pcmWave(samples: ByteArray, sampleRate: Int): ByteArray =
        ByteBuffer.allocate(44 + samples.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".encodeToByteArray())
            putInt(36 + samples.size)
            put("WAVE".encodeToByteArray())
            put("fmt ".encodeToByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".encodeToByteArray())
            putInt(samples.size)
            put(samples)
        }.array()
}
