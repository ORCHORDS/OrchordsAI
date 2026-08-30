package com.orchords.videogen.provider

import com.orchords.videogen.model.VideoGenerationRequest
import com.orchords.videogen.model.VideoGenerationTask

/**
 */
interface VideoGenerationProvider<S : VideoGenerationProviderSetting> {
    val id: String

    suspend fun create(
        setting: S,
        request: VideoGenerationRequest,
    ): Result<VideoGenerationTask>

    suspend fun query(
        setting: S,
        taskId: String,
    ): Result<VideoGenerationTask>
}
