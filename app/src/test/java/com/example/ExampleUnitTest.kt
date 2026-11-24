package com.example

import com.example.data.preferences.DepthUnit
import com.example.data.preferences.DistanceUnit
import com.example.sensors.MarineGeoUtils
import com.example.sensors.WebMercator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun haversine_distance_and_bearing_north() {
        val dist = MarineGeoUtils.distanceMeters(1.1586, 104.5812, 1.1686, 104.5812)
        assertTrue("Expected ~1.11 km north, got $dist", dist in 1050.0..1180.0)

        val bearing = MarineGeoUtils.bearingDegrees(1.1586, 104.5812, 1.1686, 104.5812)
        assertEquals(0f, bearing, 1.0f)
    }

    @Test
    fun relative_bearing_and_signed_turn() {
        assertEquals(45f, MarineGeoUtils.relativeBearing(90f, 45f), 0.01f)
        assertEquals(315f, MarineGeoUtils.relativeBearing(45f, 90f), 0.01f)
        assertEquals(-45f, MarineGeoUtils.signedTurnDegrees(45f, 90f), 0.01f)
        assertEquals("U", MarineGeoUtils.cardinalDirection(2f))
        assertEquals("T", MarineGeoUtils.cardinalDirection(90f))
    }

    @Test
    fun unit_and_dms_formatting() {
        assertEquals("18.5 m", MarineGeoUtils.formatDepth(18.5, DepthUnit.METERS))
        assertEquals("1.00 NM", MarineGeoUtils.formatDistance(1852.0, DistanceUnit.NAUTICAL_MILES))
        assertTrue(MarineGeoUtils.formatDms(1.1586, isLatitude = true).endsWith("N"))
        assertTrue(MarineGeoUtils.formatDms(104.5812, isLatitude = false).endsWith("E"))
    }

    @Test
    fun webMercator_roundTrip_projection() {
        val targetLat = 1.1842
        val targetLon = 104.6025
        val (sx, sy) = WebMercator.latLonToScreen(
            targetLat = targetLat,
            targetLon = targetLon,
            centerLat = 1.1586,
            centerLon = 104.5812,
            zoom = 13.0,
            baseTilePx = 512.0,
            viewportWidth = 1080.0,
            viewportHeight = 1920.0
        )
        val recovered = WebMercator.screenToLatLon(
            screenX = sx.toDouble(),
            screenY = sy.toDouble(),
            centerLat = 1.1586,
            centerLon = 104.5812,
            zoom = 13.0,
            baseTilePx = 512.0,
            viewportWidth = 1080.0,
            viewportHeight = 1920.0
        )
        assertEquals(targetLat, recovered.latitude, 0.0001)
        assertEquals(targetLon, recovered.longitude, 0.0001)
    }
}
