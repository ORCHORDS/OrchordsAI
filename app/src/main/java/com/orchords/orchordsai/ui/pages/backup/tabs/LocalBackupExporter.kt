package com.orchords.orchordsai.ui.pages.backup.tabs

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Pure copy helper for the local backup SAF export. Extracted so the fail-closed contract from
 * issue #366 is testable without an Android Compose harness:
 *  - If [openSink] returns `null`, the SAF provider is unhealthy and we must throw
 *    [IOException] rather than silently report success.
 *  - The copy runs to completion and closes the destination before returning.
 *  - The caller is responsible for invoking this off the main thread.
 */
internal object LocalBackupExporter {

    @Throws(IOException::class)
    fun deliverOrThrow(source: File, openSink: () -> OutputStream?) {
        val sink = openSink()
            ?: throw IOException("Destination stream unavailable; refusing to claim success")
        sink.use { outputStream ->
            FileInputStream(source).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
