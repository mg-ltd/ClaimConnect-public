package app.melosome.claimconnect.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

data class MapPoint(val latitude: Double, val longitude: Double, val accuracyM: Float?)
data class MapCapture(val bitmap: Bitmap, val offlineFallback: Boolean)

/**
 * Renders a map snapshot of the incident location with a car marker and an
 * accident-radius ring. Real tiles come from the keyless OpenFreeMap Liberty
 * style; any failure (no network, timeout, tile error) falls back to an
 * honest offline schematic so evidence capture never depends on connectivity.
 * Call [capture] on the main thread: the snapshotter requires it.
 */
object LocationSnapshot {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    /** Fixed accident radius shown on every snapshot; adjustable later if users ask. */
    const val ACCIDENT_RADIUS_M = 50.0
    const val SIZE_PX = 1024
    const val ZOOM = 17.0
    const val TIMEOUT_MS = 30_000L
    private const val TEAL = 0xFF006970.toInt()
    private const val PAPER = 0xFFF4FAFB.toInt()

    /** Point [meters] due east of [point] on a spherical earth. */
    fun destinationEast(point: MapPoint, meters: Double): MapPoint {
        val dLon = meters / (6378137.0 * cos(Math.toRadians(point.latitude)))
        return point.copy(longitude = point.longitude + Math.toDegrees(dLon))
    }

    fun coordinatesText(point: MapPoint): String =
        String.format(Locale.UK, "%.5f, %.5f", point.latitude, point.longitude)

    fun captionFor(point: MapPoint, generatedAt: String, offlineFallback: Boolean): String {
        val kind = if (offlineFallback) "Location diagram (map unavailable)" else "Map snapshot"
        return "$kind · ${coordinatesText(point)} · accident radius ${ACCIDENT_RADIUS_M.toInt()} m · $generatedAt"
    }

    suspend fun capture(context: Context, point: MapPoint, label: String): MapCapture {
        val snap = runCatching { requestSnapshot(context.applicationContext, point) }.getOrNull()
        return if (snap != null) MapCapture(overlay(context, snap, label), false)
        else MapCapture(offlineDiagram(context, point, label), true)
    }

    private class Snap(val bitmap: Bitmap, val center: PointF, val radiusPx: Float)

    private suspend fun requestSnapshot(context: Context, point: MapPoint): Snap? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val snapshotter = try {
                MapLibre.getInstance(context)
                MapSnapshotter(context, MapSnapshotter.Options(SIZE_PX, SIZE_PX)
                    .withStyleBuilder(Style.Builder().fromUri(STYLE_URL))
                    .withCameraPosition(CameraPosition.Builder()
                        .target(LatLng(point.latitude, point.longitude)).zoom(ZOOM).build())
                    .withLogo(false)
                    .withAttribution(true))
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { runCatching { snapshotter.cancel() } }
            }
            try {
                snapshotter.start({ snapshot ->
                    val result = runCatching {
                        val base = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val center = snapshot.pixelForLatLng(LatLng(point.latitude, point.longitude))
                        val edge = destinationEast(point, ACCIDENT_RADIUS_M)
                            .let { snapshot.pixelForLatLng(LatLng(it.latitude, it.longitude)) }
                        Snap(base, center, hypot(edge.x - center.x, edge.y - center.y))
                    }.getOrNull()
                    if (continuation.isActive) continuation.resume(result)
                }, { if (continuation.isActive) continuation.resume(null) })
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun overlay(context: Context, snap: Snap, label: String): Bitmap {
        val canvas = Canvas(snap.bitmap)
        canvas.drawCircle(snap.center.x, snap.center.y, snap.radiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = TEAL; alpha = 40
        })
        canvas.drawCircle(snap.center.x, snap.center.y, snap.radiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 9f; color = Color.WHITE
        })
        canvas.drawCircle(snap.center.x, snap.center.y, snap.radiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = TEAL
        })
        drawCarMarker(canvas, carGlyph(context), snap.center.x, snap.center.y)
        // Top labels only: the SDK draws tile attribution along the bottom edge and it must stay visible.
        labelBar(canvas, 24f, "Accident area · ${ACCIDENT_RADIUS_M.toInt()} m radius", 36f, bold = true)
        labelBar(canvas, 104f, label, 30f, bold = false)
        return snap.bitmap
    }

    internal fun offlineDiagram(context: Context, point: MapPoint, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)
        val grid = Paint().apply { color = 0xFFDAE4E5.toInt(); strokeWidth = 2f }
        var line = 0f
        while (line <= SIZE_PX) {
            canvas.drawLine(line, 0f, line, SIZE_PX.toFloat(), grid)
            canvas.drawLine(0f, line, SIZE_PX.toFloat(), line, grid)
            line += 64f
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF161D1D.toInt(); textAlign = Paint.Align.CENTER }
        ink.textSize = 40f; ink.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("Location diagram — map unavailable", SIZE_PX / 2f, 92f, ink)
        ink.textSize = 30f; ink.typeface = android.graphics.Typeface.DEFAULT; ink.color = 0xFF3F4849.toInt()
        canvas.drawText("Schematic — not to scale", SIZE_PX / 2f, 142f, ink)
        val center = PointF(SIZE_PX / 2f, 560f)
        canvas.drawCircle(center.x, center.y, 200f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = TEAL; alpha = 30
        })
        canvas.drawCircle(center.x, center.y, 200f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = TEAL
        })
        drawCarMarker(canvas, carGlyph(context), center.x, center.y)
        ink.textSize = 30f
        canvas.drawText("Accident radius ${ACCIDENT_RADIUS_M.toInt()} m (diagram)", SIZE_PX / 2f, 830f, ink)
        val accuracy = point.accuracyM?.let { "±${it.toInt()} m · " } ?: ""
        canvas.drawText("$accuracy${coordinatesText(point)} · $label", SIZE_PX / 2f, 880f, ink)
        return bitmap
    }

    /** Renders the Material car glyph to a bitmap, or null when unavailable. */
    internal fun carGlyph(context: Context, sizePx: Int = 56): Bitmap? = runCatching {
        val drawable = context.getDrawable(app.melosome.claimconnect.R.drawable.ic_map_car)?.mutate() ?: return null
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, Color.WHITE)
        drawable.setBounds(0, 0, sizePx, sizePx)
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { drawable.draw(Canvas(it)) }
    }.getOrNull()

    private fun drawCarMarker(canvas: Canvas, glyph: Bitmap?, cx: Float, cy: Float) {
        val teal = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = TEAL }
        val pointer = android.graphics.Path().apply {
            moveTo(cx - 20f, cy - 56f); lineTo(cx + 20f, cy - 56f); lineTo(cx, cy); close()
        }
        canvas.drawPath(pointer, teal)
        canvas.drawCircle(cx, cy - 88f, 46f, teal)
        canvas.drawCircle(cx, cy - 88f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.WHITE
        })
        if (glyph != null) {
            canvas.drawBitmap(glyph, cx - glyph.width / 2f, cy - 88f - glyph.height / 2f, null)
        } else {
            canvas.drawCircle(cx, cy - 88f, 12f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE })
        }
    }

    private fun labelBar(canvas: Canvas, top: Float, text: String, textSize: Float, bold: Boolean) {
        val bar = RectF(24f, top, SIZE_PX - 24f, top + 64f)
        canvas.drawRoundRect(bar, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.BLACK; alpha = 170
        })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.textSize = textSize; textAlign = Paint.Align.CENTER
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
        canvas.drawText(text, SIZE_PX / 2f, top + 32f + textSize * 0.35f, paint)
    }
}
