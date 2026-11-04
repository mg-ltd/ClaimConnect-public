package app.melosome.claimconnect.evidence

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import app.melosome.claimconnect.model.ProductLimits

object ImageTools {
    fun mimeType(bytes: ByteArray): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "This photograph cannot be read. Choose a different image." }
        require(options.outWidth.toLong() * options.outHeight <= ProductLimits.MAX_IMAGE_PIXELS) { "This photograph has too many pixels. Choose a smaller image." }
        return options.outMimeType?.takeIf { it in setOf("image/jpeg", "image/png", "image/webp", "image/heif", "image/heic") }
            ?: throw IllegalArgumentException("Choose a JPEG, PNG, WebP or HEIC photograph.")
    }

    fun decode(bytes: ByteArray, maxDimension: Int = 1600): Bitmap {
        mimeType(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 2) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })) { "This photograph could not be decoded." }
        val exif = runCatching { bytes.inputStream().use { ExifInterface(it) } }.getOrNull()
        val matrix = Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(oriented.width, oriented.height))
        if (scale == 1f) return oriented
        return Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== oriented) oriented.recycle() }
    }
}
