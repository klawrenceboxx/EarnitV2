package com.example.earnitv2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

class FeedbackImageProcessor(private val context: Context) {
    fun process(uri: Uri): Result<File> = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        require(mime in SUPPORTED_MIME) { "Choose a JPEG, PNG, or WebP image." }
        resolver.openAssetFileDescriptor(uri, "r")?.use {
            require(it.length < 15L * 1024 * 1024 || it.length < 0) { "That image is too large. Choose one under 15 MB." }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "We couldn't read that image." }
        var sample = 1
        while (options.outWidth / sample > 3_000 || options.outHeight / sample > 3_000) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        var bitmap = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("We couldn't read that image.")
        val orientation = resolver.openInputStream(uri)!!.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation != 0f) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        val scale = min(1f, MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (scale < 1f) bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        val directory = File(context.filesDir, "feedback_attachments").apply { mkdirs() }
        File(directory, "${UUID.randomUUID()}.jpg").also { output ->
            output.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it)) }
            bitmap.recycle()
            require(output.length() <= 4L * 1024 * 1024) { output.delete(); "The processed image is still too large." }
        }
    }

    companion object {
        private const val MAX_EDGE = 1_920
        private val SUPPORTED_MIME = setOf("image/jpeg", "image/png", "image/webp")
    }
}
