package app.melosome.claimconnect

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import app.melosome.claimconnect.location.LocationSnapshot
import app.melosome.claimconnect.location.MapPoint

@RunWith(AndroidJUnit4::class)
class MapSnapshotTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun offlineDiagramRendersWithoutNetwork() {
        val bitmap = LocationSnapshot.offlineDiagram(context, MapPoint(51.5, -0.12, 12f), "51.50000, -0.12000 · test stamp")
        try {
            assertEquals(LocationSnapshot.SIZE_PX, bitmap.width)
            assertEquals(LocationSnapshot.SIZE_PX, bitmap.height)
        } finally { bitmap.recycle() }
    }
    @Test fun carGlyphRendersFromMaterialVector() {
        val glyph = LocationSnapshot.carGlyph(context)
        assertNotNull(glyph)
        try {
            assertEquals(56, glyph!!.width)
            // The glyph must actually draw pixels (not a blank bitmap).
            var lit = 0
            for (x in 0 until glyph.width step 4) for (y in 0 until glyph.height step 4) {
                if (glyph.getPixel(x, y) ushr 24 > 0) lit++
            }
            assertTrue(lit > 10)
        } finally { glyph?.recycle() }
    }
    @Test fun mapCacheDirectoryIsExposedThroughFileProvider() {
        // Regression test: importMapSnapshot stages PNGs under cacheDir/maps and shares them
        // via FileProvider, which fails unless file_paths.xml covers that directory.
        val file = File(context.cacheDir, "maps/test/probe.png").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(16))
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            assertTrue(uri.toString().contains("maps/test/probe.png"))
        } finally { file.delete() }
    }
}
