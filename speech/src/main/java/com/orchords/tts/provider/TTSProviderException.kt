package com.orchords.tts.provider

/**
 */
class TTSProviderException(
    message: String,
    val statusCode: Int,
    cause: Throwable? = null
) : Exception(message, cause) {
    val isRetryable: Boolean
        get() = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}
