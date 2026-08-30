package com.orchords.videogen.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 *
 */
@Serializable
data class VideoGenerationRequest(
    val prompt: String? = null,
    val inputs: List<VideoGenerationInput> = emptyList(),
    val resolution: String? = null,
    val aspectRatio: String? = null,
    val durationSeconds: Int? = null,
    val generateAudio: Boolean? = null,
    val watermark: Boolean? = null,
    val seed: Long? = null,
    val promptEnhancement: Boolean? = null,
    val callbackUrl: String? = null,
    val extraParameters: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(!prompt.isNullOrBlank() || inputs.isNotEmpty()) {
            "prompt and inputs cannot both be empty"
        }
        require(durationSeconds == null || durationSeconds == -1 || durationSeconds > 0) {
            "durationSeconds must be positive or -1"
        }
        require(seed == null || seed >= 0) { "seed must be non-negative" }
    }
}

@Serializable
sealed class VideoGenerationInput {
    abstract val extra: JsonObject

    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        val role: ImageRole = ImageRole.REFERENCE,
        override val extra: JsonObject = JsonObject(emptyMap()),
    ) : VideoGenerationInput()

    @Serializable
    @SerialName("video")
    data class Video(
        val url: String,
        override val extra: JsonObject = JsonObject(emptyMap()),
    ) : VideoGenerationInput()

    @Serializable
    @SerialName("audio")
    data class Audio(
        val url: String,
        override val extra: JsonObject = JsonObject(emptyMap()),
    ) : VideoGenerationInput()

    @Serializable
    @SerialName("document")
    data class Document(
        val url: String,
        override val extra: JsonObject = JsonObject(emptyMap()),
    ) : VideoGenerationInput()

    @Serializable
    @SerialName("web_page")
    data class WebPage(
        val url: String,
        override val extra: JsonObject = JsonObject(emptyMap()),
    ) : VideoGenerationInput()

    /**
     */
    @Serializable
    @SerialName("raw")
    data class Raw(
        val value: JsonObject,
        override val extra: JsonObject = JsonObject(emptyMap()),
    ) : VideoGenerationInput()
}

@Serializable
enum class ImageRole {
    @SerialName("first_frame")
    FIRST_FRAME,

    @SerialName("last_frame")
    LAST_FRAME,

    @SerialName("reference")
    REFERENCE,
}

@Serializable
data class VideoGenerationTask(
    val id: String,
    val provider: String,
    val model: String? = null,
    val status: VideoGenerationStatus,
    val outputs: List<VideoGenerationOutput> = emptyList(),
    val error: VideoGenerationError? = null,
    val createdAtEpochSeconds: Long? = null,
    val updatedAtEpochSeconds: Long? = null,
    val usage: VideoGenerationUsage? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
) {
    val isTerminal: Boolean
        get() = status in TERMINAL_STATUSES

    companion object {
        private val TERMINAL_STATUSES = setOf(
            VideoGenerationStatus.SUCCEEDED,
            VideoGenerationStatus.FAILED,
            VideoGenerationStatus.CANCELLED,
            VideoGenerationStatus.EXPIRED,
        )
    }
}

@Serializable
enum class VideoGenerationStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    UNKNOWN,
}

@Serializable
data class VideoGenerationOutput(
    val url: String,
    val mimeType: String = "video/mp4",
    val durationSeconds: Double? = null,
    val resolution: String? = null,
    val aspectRatio: String? = null,
    val lastFrameUrl: String? = null,
)

@Serializable
data class VideoGenerationError(
    val code: String? = null,
    val message: String,
)

@Serializable
data class VideoGenerationUsage(
    val inputSeconds: Double? = null,
    val outputSeconds: Double? = null,
    val totalSeconds: Double? = null,
    val inputImageCount: Int? = null,
    val totalTokens: Long? = null,
)
