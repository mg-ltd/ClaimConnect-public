package app.melosome.claimconnect.ai

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Display copies for analysis: downscaled JPEGs. Re-encoding through
 * [Bitmap] drops EXIF (including any location) by construction; originals
 * stay encrypted at rest and are never uploaded.
 */
object DisplayCopies {
    fun jpeg(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 85): ByteArray {
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale == 1f) bitmap else Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        try {
            return ByteArrayOutputStream().use { out ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "Display copy failed." }
                out.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}
