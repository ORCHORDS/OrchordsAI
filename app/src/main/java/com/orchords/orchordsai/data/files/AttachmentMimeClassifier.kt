package com.orchords.orchordsai.data.files

private const val TEXT_MIME = "text/plain"
private const val MPEG_TS_MIME = "video/mp2t"
private const val BINARY_MIME = "application/octet-stream"
private const val REQUIRED_SYNC_BYTES = 3
private val MPEG_TS_PACKET_SIZES = intArrayOf(188, 192, 204)
private val AMBIGUOUS_TS_VIDEO_MIMES = setOf(
    "video/mp2t",
    "video/mp2ts",
    "video/mpegts",
    "video/vnd.dlna.mpeg-tts",
)

internal fun isAmbiguousTsMime(fileName: String?, providerMime: String?): Boolean {
    val extension = fileName
        ?.substringAfterLast('.', "")
        ?.lowercase()
    val normalizedMime = providerMime?.lowercase() ?: return false
    return extension == "ts" && normalizedMime in AMBIGUOUS_TS_VIDEO_MIMES
}

internal fun resolveAmbiguousTsMime(
    fileName: String?,
    providerMime: String?,
    sample: ByteArray,
): String? {
    if (!isAmbiguousTsMime(fileName, providerMime)) {
        return providerMime
    }

    return when (classifyTsSample(sample)) {
        MPEG_TS_MIME -> providerMime
        TEXT_MIME -> TEXT_MIME
        else -> providerMime
    }
}

internal fun classifyTsSample(sample: ByteArray): String = when {
    looksLikeMpegTransportStream(sample) -> MPEG_TS_MIME
    isLikelyTextSample(sample) -> TEXT_MIME
    else -> BINARY_MIME
}

internal fun looksLikeMpegTransportStream(bytes: ByteArray): Boolean {
    MPEG_TS_PACKET_SIZES.forEach { packetSize ->
        val maxOffset = minOf(packetSize - 1, 15)
        for (offset in 0..maxOffset) {
            val finalSyncIndex = offset + packetSize * (REQUIRED_SYNC_BYTES - 1)
            if (finalSyncIndex >= bytes.size) continue
            if ((0 until REQUIRED_SYNC_BYTES).all { syncIndex ->
                    (bytes[offset + packetSize * syncIndex].toInt() and 0xFF) == 0x47
                }
            ) {
                return true
            }
        }
    }
    return false
}

internal fun isLikelyTextSample(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    var printable = 0
    bytes.forEach { byte ->
        val value = byte.toInt() and 0xFF
        if (value == 0x09 || value == 0x0A || value == 0x0D || value in 0x20..0x7E) {
            printable += 1
        }
    }
    return printable.toDouble() / bytes.size >= 0.8
}
