package com.orchords.ai.provider.providers.google

import okhttp3.Request

internal const val GOOGLE_API_KEY_HEADER = "x-goog-api-key"

internal fun Request.withGoogleApiKey(apiKey: String): Request {
    require(apiKey.isNotBlank()) { "Google API key is empty" }
    return newBuilder()
        .header(GOOGLE_API_KEY_HEADER, apiKey)
        .build()
}
