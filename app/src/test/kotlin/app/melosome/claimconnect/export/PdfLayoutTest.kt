package app.melosome.claimconnect.export

import org.junit.Assert.*
import org.junit.Test

class PdfLayoutTest {
    /** Fake measurer: every character is 10pt wide. */
    private val fit: (String, Float) -> Int = { text, width -> (width / 10).toInt().coerceAtMost(text.length) }

    @Test fun wrapsAtWordBoundaries() {
        assertEquals(listOf("aa", "bb", "cc"), PdfLayout.wrap("aa bb cc", 45f, fit))
    }
    @Test fun splitsLongUnbrokenTokensAcrossLines() {
        val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val lines = PdfLayout.wrap(hash, 100f, fit)
        assertEquals(7, lines.size)
        assertTrue(lines.all { it.length <= 10 })
        assertEquals(hash, lines.joinToString(""))
    }
    @Test fun preservesBlankLinesBetweenParagraphs() {
        assertEquals(listOf("a", "", "b"), PdfLayout.wrap("a\n\nb", 100f, fit))
    }
    @Test fun trimsIndentAfterAWrappedBreak() {
        assertEquals(listOf("aa", "bb"), PdfLayout.wrap("aa   bb", 25f, fit))
    }
    @Test fun contentWidthIsPageMinusMargins() {
        assertEquals(499f, PdfLayout.CONTENT_WIDTH)
        assertEquals(PdfLayout.PAGE_WIDTH - 2 * PdfLayout.MARGIN, PdfLayout.CONTENT_WIDTH)
    }
    @Test fun landscapePhotoFillsWidthAndReservesCaptionSpace() {
        val rect = PdfLayout.photoRect(bitmapWidth = 4000, bitmapHeight = 3000, top = 200f, reservedBelow = 100f)
        assertEquals(499f, rect.width, 0.01f)
        assertEquals(4000f / 3000f, rect.width / rect.height, 0.001f)
        assertEquals(48f, rect.left, 0.01f)
        assertTrue(rect.bottom + 100f <= PdfLayout.CONTENT_BOTTOM)
    }
    @Test fun portraitPhotoIsHeightCappedAndCentred() {
        val rect = PdfLayout.photoRect(bitmapWidth = 1000, bitmapHeight = 4000, top = 200f, reservedBelow = 40f)
        assertTrue(rect.height <= 600f)
        assertEquals(48f + (499f - rect.width) / 2, rect.left, 0.01f)
        assertEquals(1000f / 4000f, rect.width / rect.height, 0.001f)
    }
    @Test fun tableRowHeightFitsTheTallerCell() {
        assertEquals(2 * 11f * 1.45f + 12f, PdfLayout.tableRowHeight(valueLines = 2, labelLines = 1, textSize = 11f, padding = 6f), 0.001f)
    }
}
