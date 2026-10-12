package com.example.fixup.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun compressImage(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        return stream.toByteArray()
    }

    // Images are stored inline in Firestore as "data:image/jpeg;base64,..." strings
    // (Firebase Storage needs the paid Blaze plan). Glide loads these strings directly.
    // Keep them small: a Firestore document is capped at 1 MB.
    fun bitmapToDataUri(bitmap: Bitmap, maxDim: Int = 800, quality: Int = 60): String {
        val scaled = scaleDown(bitmap, maxDim)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /** Decodes a picked image, applies EXIF rotation, downsizes it and returns a data URI ("" on failure). */
    fun uriToDataUri(context: Context, uri: Uri, maxDim: Int = 800, quality: Int = 60): String {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return ""

            val degrees = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (degrees != 0f) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(degrees) }, true)
            }
            bitmapToDataUri(bitmap, maxDim, quality)
        } catch (e: Exception) {
            ""
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
        )
    }
}
