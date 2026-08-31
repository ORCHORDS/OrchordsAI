package com.orchords.orchordsai.data.ai

import java.io.File
import java.util.UUID

private const val TOOL_OUTPUT_ARTIFACT_ATTEMPTS = 8

/**
 * Persists a tool result under an app-generated opaque filename. Provider-supplied tool-call IDs
 * are deliberately not accepted by this filesystem boundary.
 */
internal fun persistToolOutputArtifact(outputRoot: File, content: String): File {
    require(outputRoot.mkdirs() || outputRoot.isDirectory) { "Unable to create tool output directory" }
    val root = outputRoot.canonicalFile

    repeat(TOOL_OUTPUT_ARTIFACT_ATTEMPTS) {
        val artifact = File(root, "${UUID.randomUUID()}.txt").canonicalFile
        require(artifact.parentFile == root) { "Tool output path escaped its managed root" }
        if (artifact.createNewFile()) {
            artifact.writeText(content)
            return artifact
        }
    }

    error("Unable to allocate unique tool output artifact")
}
