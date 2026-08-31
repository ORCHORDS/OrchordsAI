package com.orchords.ai.provider.stream

import kotlinx.serialization.Serializable
import com.orchords.ai.provider.requireProviderSseEventWithinLimit

/**
 *
 */
@Serializable
data class SseEvent(
    val id: String? = null,
    val event: String? = null,
    val data: String,
    val retryMillis: Long? = null,
) {
    init {
        requireProviderSseEventWithinLimit(data)
    }
}
