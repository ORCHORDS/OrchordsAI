package com.orchords.orchordsai.data.files

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.uuid.Uuid

object FileUtils {
    private const val TAG = "FileUtils"
    private const val MIME_SNIFF_BYTES = 640

    fun buildUuidFileName(displayName: String?, mimeType: String?): String {
        val extFromName = displayName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() && it != displayName }
            ?.lowercase()
        val extFromMime = mimeType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it.lowercase()) }
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
        val ext = extFromName ?: extFromMime ?: "bin"
        return "${Uuid.random()}.$ext"
    }

    fun buildRelativePath(folder: String, file: File): String =
        "$folder/${file.name}"

    fun getRelativePathInFilesDir(filesDir: File, file: File): String? {
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val canonicalFilesDir = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val basePath = canonicalFilesDir.path
        val filePath = canonicalFile.path
        if (!filePath.startsWith("$basePath${File.separator}")) {
            return null
        }
        return canonicalFile.relativeTo(canonicalFilesDir).path.replace(File.separatorChar, '/')
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            var fileName: String? = null
            val projection = arrayOf(
                OpenableColumns.DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val documentDisplayNameIndex =
                        cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (documentDisplayNameIndex != -1) {
                        fileName = cursor.getString(documentDisplayNameIndex)
                    } else {
                        val openableDisplayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (openableDisplayNameIndex != -1) {
                            fileName = cursor.getString(openableDisplayNameIndex)
                        }
                    }
                }
            }
            fileName
        }.onFailure {
            Log.w(TAG, "getFileNameFromUri: Failed to query display name for $uri", it)
        }.getOrNull()
    }

    fun getFileMimeType(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> runCatching {
                val providerMime = context.contentResolver.getType(uri)
                val fileName = getFileNameFromUri(context, uri)
                if (!isAmbiguousTsMime(fileName, providerMime)) {
                    providerMime
                } else {
                    resolveAmbiguousTsMime(
                        fileName = fileName,
                        providerMime = providerMime,
                        sample = readUriSample(context, uri),
                    )
                }
            }.onFailure {
                Log.w(TAG, "getFileMimeType: Failed to resolve MIME for $uri", it)
            }.getOrNull()
            else -> null
        }
    }

    fun guessMimeType(file: File, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "ts") {
            return classifyTsSample(readFileSample(file))
        }
        if (ext.isNotEmpty()) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }
        return sniffMimeType(file)
    }

    fun compressBitmapToPng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }

    private fun sniffMimeType(file: File): String {
        val sample = readFileSample(file)
        if (sample.isEmpty()) return "application/octet-stream"

        if (sample.startsWithBytes(0x89, 0x50, 0x4E, 0x47)) return "image/png"
        if (sample.startsWithBytes(0xFF, 0xD8, 0xFF)) return "image/jpeg"
        if (sample.startsWithBytes(0x47, 0x49, 0x46, 0x38)) return "image/gif"
        if (sample.startsWithBytes(0x25, 0x50, 0x44, 0x46)) return "application/pdf"
        if (sample.startsWithBytes(0x50, 0x4B, 0x03, 0x04)) return "application/zip"
        if (sample.startsWithBytes(0x50, 0x4B, 0x05, 0x06)) return "application/zip"
        if (sample.startsWithBytes(0x50, 0x4B, 0x07, 0x08)) return "application/zip"
        if (sample.size >= 12 &&
            sample.startsWithBytes(0x52, 0x49, 0x46, 0x46) &&
            sample.sliceArray(8..11).contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))
        ) {
            return "image/webp"
        }
        if (sample.size >= 12 && sample.sliceArray(4..7).toString(Charsets.US_ASCII) == "ftyp") {
            when (sample.sliceArray(8..11).toString(Charsets.US_ASCII)) {
                "heic", "heix", "heim", "heis",
                "hevc", "hevx", "hevm", "hevs",
                "mif1", "msf1", "heif",
                    -> return "image/heic"

                "avif", "avis" -> return "image/avif"
            }
        }

        if (isLikelyTextSample(sample)) {
            return "text/plain"
        }

        return "application/octet-stream"
    }

    private fun readUriSample(context: Context, uri: Uri): ByteArray = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBoundedSample()
        } ?: ByteArray(0)
    }.getOrDefault(ByteArray(0))

    private fun readFileSample(file: File): ByteArray = runCatching {
        FileInputStream(file).use { input ->
            input.readBoundedSample()
        }
    }.getOrDefault(ByteArray(0))

    private fun InputStream.readBoundedSample(): ByteArray {
        val sample = ByteArray(MIME_SNIFF_BYTES)
        val length = read(sample)
        return if (length > 0) sample.copyOf(length) else ByteArray(0)
    }

    private fun ByteArray.startsWithBytes(vararg values: Int): Boolean {
        if (this.size < values.size) return false
        for (i in values.indices) {
            if ((this[i].toInt() and 0xFF) != values[i]) return false
        }
        return true
    }
}
