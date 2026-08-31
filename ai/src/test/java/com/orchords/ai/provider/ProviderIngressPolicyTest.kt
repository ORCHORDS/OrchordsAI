package com.orchords.ai.provider

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderIngressPolicyTest {
    @Test
    fun `response body at byte limit remains readable`() {
        val body = "hello".toResponseBody("application/json".toMediaType())
            .withProviderByteLimit(5)

        assertEquals("hello", body.string())
    }

    @Test
    fun `declared response above byte limit is rejected before materialization`() {
        val error = assertThrows(ProviderPayloadTooLargeException::class.java) {
            "abcdef".toResponseBody("application/json".toMediaType())
                .withProviderByteLimit(5)
        }

        assertEquals(5, error.limitBytes)
        assertEquals(6, error.observedBytes)
    }

    @Test
    fun `SSE ASCII payload over byte limit is rejected`() {
        assertThrows(ProviderPayloadTooLargeException::class.java) {
            requireProviderSseEventWithinLimit("abcdef", maxBytes = 5)
        }
    }

    @Test
    fun `SSE UTF8 accounting handles multi byte characters without encoding allocation`() {
        requireProviderSseEventWithinLimit("a€😀", maxBytes = 8)

        val error = assertThrows(ProviderPayloadTooLargeException::class.java) {
            requireProviderSseEventWithinLimit("a€😀", maxBytes = 7)
        }
        assertEquals(7, error.limitBytes)
        assertEquals(8, error.observedBytes)
    }

    @Test
    fun `SSE exact byte limit is accepted`() {
        requireProviderSseEventWithinLimit("12345678", maxBytes = 8)
    }
}
