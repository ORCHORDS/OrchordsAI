@file:Suppress("unused")

package com.orchords.orchordsai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 */
object ImageUtils {

    /**
     *
     */
    fun loadOptimizedBitmap(
        context: Context,
        uri: Uri,
        maxSize: Int = 1024
    ): Bitmap? {
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val sampleSize = calculateInSampleSize(options, maxSize, maxSize)

            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, loadOptions)
            }

            bitmap?.let { correctImageOrientation(context, uri, it) }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     *
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     *
     */
    fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            inputStream?.close()

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        }.onFailure {
            it.printStackTrace()
        }.getOrDefault(bitmap)
    }

    /**
     */
    fun isHeifImage(context: Context, uri: Uri): Boolean {
        context.contentResolver.getType(uri)?.lowercase()?.let { mime ->
            if (mime.contains("heic") || mime.contains("heif")) return true
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 12) return@use false
                if (header.copyOfRange(4, 8).toString(Charsets.US_ASCII) != "ftyp") return@use false
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) in HEIF_BRANDS
            } ?: false
        }.getOrDefault(false)
    }

    /**
     *
     */
    fun convertHeifToJpeg(
        context: Context,
        uri: Uri,
        target: File,
        maxSize: Int = 4096,
        quality: Int = 95,
    ): Boolean = runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }
        val loadOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, maxSize, maxSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, loadOptions)
        } ?: return@runCatching false
        val oriented = correctImageOrientation(context, uri, decoded)
        try {
            target.outputStream().use { output ->
                oriented.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            true
        } finally {
            recycleBitmapSafely(oriented)
        }
    }.onFailure {
        it.printStackTrace()
    }.getOrDefault(false)

    private val HEIF_BRANDS = setOf(
        "heic", "heix", "heim", "heis",
        "hevc", "hevx", "hevm", "hevs",
        "mif1", "msf1", "heif",
    )

    /**
     *
     */
    fun decodeQRCodeFromBitmap(bitmap: Bitmap): String? {
        return runCatching {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)

            result.text
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     *
     */
    fun decodeQRCodeFromUri(
        context: Context,
        uri: Uri,
        maxSize: Int = 1024
    ): String? {
        val bitmap = loadOptimizedBitmap(context, uri, maxSize) ?: return null
        return try {
            decodeQRCodeFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     *
     */
    fun recycleBitmapSafely(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
    }

    /**
     *
     */
    fun getImageInfo(context: Context, uri: Uri): ImageInfo? {
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            if (options.outWidth > 0 && options.outHeight > 0) {
                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    mimeType = options.outMimeType
                )
            } else null
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     *
     */
    fun getTavernCharacterMeta(context: Context, uri: Uri): Result<String> = runCatching {
        val metadata = context.contentResolver.openInputStream(uri)?.use { ImageMetadataReader.readMetadata(it) }
        if (metadata == null) error("Metadata is null, please check if the image is a character card")
        if (!metadata.containsDirectoryOfType(PngDirectory::class.java)) error("No PNG directory found, please check if the image is a character card")

        val pngDirectory = metadata.getDirectoriesOfType(PngDirectory::class.java)
            .firstOrNull { directory ->
                directory.pngChunkType == PngChunkType.tEXt
                    && directory.getString(PngDirectory.TAG_TEXTUAL_DATA).startsWith("[chara:")
            } ?: error("No tEXt chunk found, please check if the image is a character card")

        val value = pngDirectory.getString(PngDirectory.TAG_TEXTUAL_DATA)

        val regex = Regex("""\[chara:\s*(.+?)]""")
        return Result.success(regex.find(value)?.groupValues?.get(1) ?: error("No character data found"))
    }

    data class ImageInfo(
        val width: Int,
        val height: Int,
        val mimeType: String?
    )
}
