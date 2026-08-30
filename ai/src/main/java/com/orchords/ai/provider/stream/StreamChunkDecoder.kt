package com.orchords.ai.provider.stream

import com.orchords.ai.ui.StreamChunk

/**
 */
interface StreamChunkDecoder {
    fun accept(event: SseEvent): DecodeResult

    fun onClosed(): List<StreamChunk>
}

data class DecodeResult(
    val chunks: List<StreamChunk> = emptyList(),
    val completed: Boolean = false,
)
