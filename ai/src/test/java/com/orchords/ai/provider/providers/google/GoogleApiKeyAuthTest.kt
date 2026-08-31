package com.orchords.ai.provider.providers.google

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleApiKeyAuthTest {
    @Test
    fun `api key uses header and never changes URL query`() {
        val request = Request.Builder()
            .url("https://aiplatform.googleapis.com/v1/models?alt=sse")
            .get()
            .build()

        val authenticated = request.withGoogleApiKey("fixture-google-key")

        assertEquals("fixture-google-key", authenticated.header(GOOGLE_API_KEY_HEADER))
        assertEquals("sse", authenticated.url.queryParameter("alt"))
        assertFalse(authenticated.url.queryParameterNames.contains("key"))
        assertFalse(authenticated.url.toString().contains("fixture-google-key"))
    }

    @Test
    fun `existing API key header is replaced without duplicating it`() {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .header(GOOGLE_API_KEY_HEADER, "old-key")
            .build()

        val authenticated = request.withGoogleApiKey("new-key")

        assertEquals(listOf("new-key"), authenticated.headers.values(GOOGLE_API_KEY_HEADER))
        assertFalse(authenticated.url.toString().contains("new-key"))
    }

    @Test
    fun `blank API key fails before network request construction completes`() {
        val request = Request.Builder()
            .url("https://aiplatform.googleapis.com/v1/models")
            .build()

        assertThrows(IllegalArgumentException::class.java) {
            request.withGoogleApiKey("   ")
        }
    }
}
