package app.melosome.claimconnect.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.melosome.claimconnect.ai.AcceptedNote
import app.melosome.claimconnect.evidence.ImageTools
import app.melosome.claimconnect.model.*

class PdfPackGenerator {
    fun generate(detail: IncidentDetail, generatedAt: Long, aiNotes: List<AcceptedNote> = emptyList(), original: (Evidence) -> ByteArray): ByteArray {
        require(detail.incident.validationErrors().isEmpty()) { "Complete the incident details before generating a pack." }
        val pdf = PdfDocument()
        val writer = Writer(pdf, detail.incident.reference)
        try {
            val incident = detail.incident
            writer.heading("Incident evidence pack", 22f)
            writer.table(listOf(
                "Reference" to incident.reference,
                "Revision" to incident.revision.toString(),
                "Generated" to timestamp(generatedAt),
            ))
            writer.box("All incident details and captions are user-supplied and unverified.")
            writer.heading("Vehicle and driver")
            writer.table(listOf(
                "Registration" to incident.registration,
                "Vehicle" to incident.vehicle,
                "Driver" to incident.driver,
                "Company / fleet" to incident.company,
            ))
            writer.heading("Incident")
            writer.table(listOf(
                "Date and time (user-entered)" to "${incident.date} ${incident.time} (${incident.timeZone})",
                "Location (user-entered)" to incident.location,
            ))
            writer.heading("Driver account")
            writer.box(incident.notes)
            writer.heading("Other parties and witnesses")
            writer.box(incident.otherParties.ifBlank { "No details supplied." })
            writer.heading("Product limits")
            writer.box(ProductLimits.NOTICE + "\n\n" +
                "A photograph hash identifies the original bytes held by this app. It does not verify when or where " +
                "a photograph was taken or whether its content is authentic. Photographs in this PDF are scaled display " +
                "copies; their hashes are hashes of the original files, not the PDF image bytes.")
            writer.heading("Evidence manifest (${photoCount(detail.evidence.size)})")
            if (detail.evidence.isEmpty()) writer.box("No photographs attached.")
            detail.evidence.forEachIndexed { index, evidence ->
                writer.heading("Photo ${index + 1}", 13f)
                writer.table(buildList {
                    add("Evidence ID" to evidence.id)
                    add("Source" to evidence.source)
                    add("Original filename" to evidence.originalName)
                    add("Added to app" to timestamp(evidence.addedAt))
                    add("Original format / bytes" to "${evidence.mimeType} / ${evidence.sizeBytes}")
                    add("SHA-256 of original" to evidence.sha256)
                    if (evidence.caption.isNotBlank()) add("User caption" to evidence.caption)
                })
            }
            if (aiNotes.isNotEmpty()) {
                writer.heading("AI-assisted observations (unverified)")
                writer.line("The following observations were suggested by AI and kept by the user. " +
                    "They are unverified and describe display copies, not the original photographs.")
                aiNotes.forEach { note ->
                    writer.table(listOf(
                        "Observation" to note.text,
                        "Photos" to note.photoNumbers.joinToString(", ") { "Photo $it" }.ifBlank { "None recorded" },
                        "Model" to note.modelVersion,
                    ), labelWidth = 110f)
                }
            }
            detail.evidence.forEachIndexed { index, evidence ->
                val bytes = original(evidence)
                check(sha256(bytes) == evidence.sha256) { "A photograph failed its integrity check. The pack was not generated." }
                val bitmap = ImageTools.decode(bytes)
                try {
                    writer.newPage()
                    writer.heading("Photo ${index + 1}", 20f)
                    writer.table(listOf(
                        "Evidence ID" to evidence.id,
                        "SHA-256 of original" to evidence.sha256,
                        "Added to app" to timestamp(evidence.addedAt),
                        "Source" to evidence.source,
                    ), labelWidth = 130f, size = 10f)
                    writer.gap(10f)
                    val captionBoxHeight = if (evidence.caption.isBlank()) 0f
                    else writer.measureBox(evidence.caption, 11f) + 10f
                    val rect = PdfLayout.photoRect(bitmap.width, bitmap.height, writer.y, captionBoxHeight + 10f)
                    writer.canvas.drawBitmap(bitmap, null,
                        RectF(rect.left, rect.top, rect.right, rect.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
                    writer.y = rect.bottom + 10f
                    if (evidence.caption.isNotBlank()) writer.box(evidence.caption)
                } finally { bitmap.recycle() }
            }
            writer.finish()
            return ByteArrayOutputStream().use { output -> pdf.writeTo(output); output.toByteArray() }
        } finally { writer.finish(); pdf.close() }
    }

    private class Writer(val pdf: PdfDocument, val reference: String) {
        private var page: PdfDocument.Page? = null
        private var pageNumber = 0
        val canvas get() = requireNotNull(page).canvas
        var y = 76f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        private val fill = Paint().apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = GRID }
        init { newPage() }
        fun finish() {
            page?.let {
                paint.textSize = 9f; paint.typeface = Typeface.DEFAULT
                it.canvas.drawText("$reference · Page $pageNumber", PdfLayout.MARGIN, 809f, paint)
                pdf.finishPage(it)
            }
            page = null
        }
        fun newPage() {
            finish()
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            paint.textSize = 12f; paint.typeface = Typeface.DEFAULT_BOLD; paint.color = TEAL
            canvas.drawText("CLAIMCONNECT", PdfLayout.MARGIN, 42f, paint)
            paint.color = INK
            y = 76f
        }
        fun gap(points: Float) { y += points }
        fun heading(text: String, size: Float = 16f) {
            if (y > 720f) newPage()
            y += 12f
            line(text, size, true)
            stroke.strokeWidth = 2f; stroke.color = TEAL
            canvas.drawLine(PdfLayout.MARGIN, y + 2f, PdfLayout.MARGIN + PdfLayout.CONTENT_WIDTH, y + 2f, stroke)
            stroke.strokeWidth = 1f; stroke.color = GRID
            y += 12f
        }
        /** Two-column key-value table with a shaded label column and grid lines. Rows flow across pages. */
        fun table(rows: List<Pair<String, String>>, labelWidth: Float = 150f, size: Float = 11f) {
            if (rows.isEmpty()) return
            val pad = 6f
            val lineHeight = size * PdfLayout.LINE_HEIGHT
            rows.forEach { (label, rawValue) ->
                val value = rawValue.ifBlank { "Not supplied" }
                paint.textSize = size
                paint.typeface = Typeface.DEFAULT_BOLD
                val labelLines = PdfLayout.wrap(label, labelWidth - 2 * pad) { text, width ->
                    paint.breakText(text, true, width, null)
                }
                paint.typeface = Typeface.DEFAULT
                val valueLines = PdfLayout.wrap(value, PdfLayout.CONTENT_WIDTH - labelWidth - 2 * pad) { text, width ->
                    paint.breakText(text, true, width, null)
                }
                var labelShown = 0
                var valueShown = 0
                while (labelShown < labelLines.size || valueShown < valueLines.size) {
                    val maxLines = ((PdfLayout.CONTENT_BOTTOM - y - 2 * pad) / lineHeight).toInt()
                    if (maxLines <= 0) {
                        newPage()
                        continue
                    }
                    val takeLabel = (labelLines.size - labelShown).coerceAtMost(maxLines)
                    val takeValue = (valueLines.size - valueShown).coerceAtMost(maxLines).coerceAtLeast(
                        if (valueShown < valueLines.size) 1 else 0)
                    val rowHeight = PdfLayout.tableRowHeight(takeValue, takeLabel, size, pad)
                    val left = PdfLayout.MARGIN
                    val divider = left + labelWidth
                    val right = left + PdfLayout.CONTENT_WIDTH
                    fill.color = LABEL_SHADE
                    canvas.drawRect(left, y, divider, y + rowHeight, fill)
                    canvas.drawRect(left, y, right, y + rowHeight, stroke)
                    canvas.drawLine(divider, y, divider, y + rowHeight, stroke)
                    paint.textSize = size
                    paint.typeface = Typeface.DEFAULT_BOLD
                    labelLines.drop(labelShown).take(takeLabel).forEachIndexed { i, line ->
                        canvas.drawText(line, left + pad, y + pad + lineHeight * (i + 1) - 4f, paint)
                    }
                    paint.typeface = Typeface.DEFAULT
                    valueLines.drop(valueShown).take(takeValue).forEachIndexed { i, line ->
                        canvas.drawText(line, divider + pad, y + pad + lineHeight * (i + 1) - 4f, paint)
                    }
                    y += rowHeight
                    labelShown += takeLabel
                    valueShown += takeValue
                }
            }
            y += 6f
        }
        /** Shaded bordered paragraph for notices, narratives and captions. Flows across pages. */
        fun box(text: String, size: Float = 11f) {
            paint.textSize = size
            paint.typeface = Typeface.DEFAULT
            val pad = 8f
            val lineHeight = size * PdfLayout.LINE_HEIGHT
            val lines = PdfLayout.wrap(text, PdfLayout.CONTENT_WIDTH - 2 * pad) { remaining, width ->
                paint.breakText(remaining, true, width, null)
            }
            var shown = 0
            while (shown < lines.size) {
                val maxLines = ((PdfLayout.CONTENT_BOTTOM - y - 2 * pad) / lineHeight).toInt()
                if (maxLines <= 0) {
                    newPage()
                    continue
                }
                val take = (lines.size - shown).coerceAtMost(maxLines)
                val boxHeight = take * lineHeight + 2 * pad
                val left = PdfLayout.MARGIN
                fill.color = BOX_SHADE
                canvas.drawRect(left, y, left + PdfLayout.CONTENT_WIDTH, y + boxHeight, fill)
                canvas.drawRect(left, y, left + PdfLayout.CONTENT_WIDTH, y + boxHeight, stroke)
                paint.textSize = size
                paint.typeface = Typeface.DEFAULT
                lines.drop(shown).take(take).forEachIndexed { i, line ->
                    canvas.drawText(line, left + pad, y + pad + lineHeight * (i + 1) - 4f, paint)
                }
                y += boxHeight
                shown += take
            }
            y += 6f
        }
        /** Height a [box] call would occupy on one page, used to reserve caption space under photos. */
        fun measureBox(text: String, size: Float): Float {
            paint.textSize = size
            paint.typeface = Typeface.DEFAULT
            val lines = PdfLayout.wrap(text, PdfLayout.CONTENT_WIDTH - 16f) { remaining, width ->
                paint.breakText(remaining, true, width, null)
            }
            return lines.size * size * PdfLayout.LINE_HEIGHT + 16f
        }
        fun line(text: String, size: Float = 11f, bold: Boolean = false) {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            PdfLayout.wrap(text, PdfLayout.CONTENT_WIDTH) { remaining, width ->
                paint.breakText(remaining, true, width, null)
            }.forEach { fragment ->
                if (y + size > PdfLayout.CONTENT_BOTTOM) {
                    newPage(); paint.textSize = size
                    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                canvas.drawText(fragment, PdfLayout.MARGIN, y, paint)
                y += size * PdfLayout.LINE_HEIGHT
            }
        }
    }

    companion object {
        private val INK = Color.rgb(27, 44, 41)
        private val TEAL = Color.rgb(0, 105, 112)
        private val LABEL_SHADE = Color.rgb(234, 242, 243)
        private val BOX_SHADE = Color.rgb(244, 250, 251)
        private val GRID = Color.rgb(111, 121, 122)
        fun timestamp(millis: Long): String = DateTimeFormatter.ofPattern("d MMM uuuu HH:mm:ss z", Locale.UK)
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
    }
}
