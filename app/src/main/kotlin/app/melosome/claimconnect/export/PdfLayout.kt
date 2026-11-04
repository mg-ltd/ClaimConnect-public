package app.melosome.claimconnect.export

/**
 * Framework-free layout math for the evidence-pack PDF. This object deliberately
 * avoids every `android.*` type so its behaviour is covered by JVM unit tests;
 * [PdfPackGenerator] supplies a [android.graphics.Paint.breakText]-based
 * measurer and draws the geometry computed here.
 */
object PdfLayout {
    const val PAGE_WIDTH = 595f
    const val PAGE_HEIGHT = 842f
    const val MARGIN = 48f
    const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    /** Lowest y at which body content may be drawn; the footer sits below this. */
    const val CONTENT_BOTTOM = 775f
    /** Line spacing as a multiple of the text size. */
    const val LINE_HEIGHT = 1.45f
    /** Display photos are never taller than this, so meta text and captions keep room. */
    const val MAX_PHOTO_HEIGHT = 600f

    /**
     * Splits [text] into draw lines of at most [maxWidth]. [fit] mirrors
     * `Paint.breakText`: it returns how many leading characters of its input fit.
     * Breaks fall on word boundaries where possible; long unbroken tokens (hashes,
     * IDs) are split mid-token. Blank paragraphs yield one empty line each.
     */
    fun wrap(text: String, maxWidth: Float, fit: (String, Float) -> Int): List<String> {
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var remaining = paragraph
            if (remaining.isEmpty()) {
                lines.add("")
                return@forEach
            }
            while (remaining.isNotEmpty()) {
                var count = fit(remaining, maxWidth).coerceAtLeast(1).coerceAtMost(remaining.length)
                if (count < remaining.length) {
                    val space = remaining.lastIndexOf(' ', count - 1)
                    if (space > 0) count = space
                }
                lines.add(remaining.take(count))
                remaining = remaining.drop(count).trimStart()
            }
        }
        return lines
    }

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * Display rectangle for a photo of [bitmapWidth] x [bitmapHeight] starting at
     * [top]. The image keeps its aspect ratio, never exceeds the content width or
     * [MAX_PHOTO_HEIGHT], is centred horizontally, and leaves [reservedBelow]
     * points under it (for the caption box) above [CONTENT_BOTTOM].
     */
    fun photoRect(bitmapWidth: Int, bitmapHeight: Int, top: Float, reservedBelow: Float): Rect {
        val height = MAX_PHOTO_HEIGHT.coerceAtMost(CONTENT_BOTTOM - reservedBelow - top).coerceAtLeast(120f)
        val scale = minOf(CONTENT_WIDTH / bitmapWidth, height / bitmapHeight)
        val width = bitmapWidth * scale
        val displayHeight = bitmapHeight * scale
        val left = MARGIN + (CONTENT_WIDTH - width) / 2
        return Rect(left, top, left + width, top + displayHeight)
    }

    /** Height of a table row holding [valueLines] and [labelLines] wrapped lines. */
    fun tableRowHeight(valueLines: Int, labelLines: Int, textSize: Float, padding: Float): Float =
        maxOf(valueLines, labelLines) * textSize * LINE_HEIGHT + padding * 2
}
