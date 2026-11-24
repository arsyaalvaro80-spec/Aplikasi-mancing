package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sensors.MarineGeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Bintan Sonar AI", appName)
    }

    @Test
    fun `bearing and haversine distance calculation`() {
        val dist = MarineGeoUtils.distanceMeters(1.1586, 104.5812, 1.1686, 104.5812)
        assertTrue("Expected ~1.11 km north, got $dist", dist in 1050.0..1180.0)

        val bearing = MarineGeoUtils.bearingDegrees(1.1586, 104.5812, 1.1686, 104.5812)
        assertEquals(0f, bearing, 1.0f)

        val rel = MarineGeoUtils.relativeBearing(targetBearing = 90f, currentHeading = 45f)
        assertEquals(45f, rel, 0.01f)
    }
}
