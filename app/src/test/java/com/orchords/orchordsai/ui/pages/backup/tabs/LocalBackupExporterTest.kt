package com.orchords.orchordsai.ui.pages.backup.tabs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Pins the fail-closed contract from issue #366: a null SAF destination stream must NOT be
 * reported as success, and the source ZIP must be copied byte-for-byte to the destination when
 * delivery does succeed.
 */
class LocalBackupExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `null destination stream throws IOException and does not silently succeed`() {
        val source = tempFolder.newFile("staged.zip").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val ex = assertThrows(IOException::class.java) {
            LocalBackupExporter.deliverOrThrow(source = source, openSink = { null })
        }
        // The throw message must make the failure visible to operators / logcat.
        check(ex.message!!.contains("Destination stream unavailable")) {
            "Unexpected error message: ${ex.message}"
        }
    }

    @Test
    fun `successful copy writes the staged bytes into the destination`() {
        val sourceBytes = ByteArray(8 * 1024) { (it % 251).toByte() }
        val source = tempFolder.newFile("staged.zip").apply { writeBytes(sourceBytes) }
        val sink = ByteArrayOutputStream()

        LocalBackupExporter.deliverOrThrow(source = source, openSink = { sink })

        assertArrayEquals(sourceBytes, sink.toByteArray())
    }

    @Test
    fun `destination sink is closed after a successful copy`() {
        val source = tempFolder.newFile("staged.zip").apply {
            writeBytes(byteArrayOf(0x10, 0x20))
        }
        var closed = false
        LocalBackupExporter.deliverOrThrow(source = source, openSink = {
            object : java.io.ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        })
        check(closed) { "Destination sink was not closed after delivery" }
    }

    @Test
    fun `IOException during copy propagates and leaves no partial promise of delivery`() {
        val source = File(tempFolder.root, "staged.zip").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        assertThrows(IOException::class.java) {
            LocalBackupExporter.deliverOrThrow(source = source, openSink = {
                object : ByteArrayOutputStream() {
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        throw IOException("Disk full")
                    }
                }
            })
        }
    }
}
