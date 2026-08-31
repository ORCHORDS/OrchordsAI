package com.orchords.tts.model

/**
 */
enum class PlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Paused,
    Ended,
    Error
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    /** Number of chunks that have completed playback successfully. */
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val errorMessage: String? = null,
    /** Text is retained in memory only so the failed segment can be recovered. */
    val failedChunkText: String? = null,
    val failedChunkIndex: Int? = null,
    val failedChunkRetryable: Boolean = false,
    val skippedChunks: Int = 0
)


