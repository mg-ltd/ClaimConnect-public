package app.melosome.claimconnect.location

import org.junit.Assert.*
import org.junit.Test
import app.melosome.claimconnect.model.ProductLimits

class LocationSnapshotTest {
    @Test fun fiftyMetresDueEastAtTheEquator() {
        val moved = LocationSnapshot.destinationEast(MapPoint(0.0, 0.0, null), 50.0)
        assertEquals(0.0, moved.latitude, 0.0)
        assertEquals(0.0004491, moved.longitude, 1e-7)
    }
    @Test fun eastOffsetGrowsTowardThePoles() {
        val equator = LocationSnapshot.destinationEast(MapPoint(0.0, 0.0, null), 50.0).longitude
        val sixty = LocationSnapshot.destinationEast(MapPoint(60.0, 0.0, null), 50.0).longitude
        assertEquals(2.0, sixty / equator, 0.001)
    }
    @Test fun coordinatesUseFiveDecimals() {
        assertEquals("51.50735, -0.12776", LocationSnapshot.coordinatesText(MapPoint(51.507351, -0.127758, null)))
    }
    @Test fun captionsNameSourceCoordinatesAndRadius() {
        val point = MapPoint(51.5, -0.12, null)
        assertEquals("Map snapshot · 51.50000, -0.12000 · accident radius 50 m · stamp",
            LocationSnapshot.captionFor(point, "stamp", false))
        assertEquals("Location diagram (map unavailable) · 51.50000, -0.12000 · accident radius 50 m · stamp",
            LocationSnapshot.captionFor(point, "stamp", true))
    }
    @Test fun snapshotSizeStaysWithinPixelLimits() {
        assertEquals(50.0, LocationSnapshot.ACCIDENT_RADIUS_M, 0.0)
        assertTrue(LocationSnapshot.SIZE_PX.toLong() * LocationSnapshot.SIZE_PX < ProductLimits.MAX_IMAGE_PIXELS)
    }
}
