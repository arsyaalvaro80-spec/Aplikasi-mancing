package com.example.sensors

import com.example.data.preferences.DepthUnit
import com.example.data.preferences.DistanceUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object MarineGeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_NAUTICAL_MILE = 1852.0
    private const val FEET_PER_METER = 3.28084

    // Reference coordinates for Bintan Island waters (Tanjung Berakit / Trikora coast)
    const val BINTAN_REF_LAT = 1.1586
    const val BINTAN_REF_LON = 104.5812

    /**
     * Computes great-circle distance in meters using the Haversine formula.
     */
    fun distanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Double {
        val dLat = Math.toRadians(toLat - fromLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Computes initial forward bearing in degrees [0, 360) from origin to target.
     */
    fun bearingDegrees(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Float {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLon - fromLon)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val theta = atan2(y, x)
        val degrees = (Math.toDegrees(theta) + 360.0) % 360.0
        return degrees.toFloat()
    }

    /**
     * Computes relative angle in degrees [0, 360) pointing from current heading to target bearing.
     */
    fun relativeBearing(targetBearing: Float, currentHeading: Float): Float {
        return ((targetBearing - currentHeading) % 360f + 360f) % 360f
    }

    /**
     * Signed turn delta in [-180, 180] degrees (positive = turn right / starboard, negative = left / port).
     */
    fun signedTurnDegrees(targetBearing: Float, currentHeading: Float): Float {
        val rel = relativeBearing(targetBearing, currentHeading)
        return if (rel > 180f) rel - 360f else rel
    }

    fun formatDms(coordinate: Double, isLatitude: Boolean): String {
        val hemisphere = if (isLatitude) {
            if (coordinate >= 0) "N" else "S"
        } else {
            if (coordinate >= 0) "E" else "W"
        }
        val absVal = abs(coordinate)
        val degrees = floor(absVal).toInt()
        val minutesFull = (absVal - degrees) * 60.0
        val minutes = floor(minutesFull).toInt()
        val seconds = (minutesFull - minutes) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, hemisphere)
    }

    fun formatDecimalCoords(lat: Double, lon: Double): String {
        return String.format(Locale.US, "%.5f°, %.5f°", lat, lon)
    }

    fun cardinalDirection(degrees: Float): String {
        val normalized = ((degrees % 360f) + 360f) % 360f
        val directions = listOf("U", "TL", "T", "TG", "S", "BD", "B", "BL")
        val index = ((normalized + 22.5f) / 45f).toInt() % 8
        return directions[index]
    }

    fun formatDistance(distanceMeters: Double, unit: DistanceUnit): String {
        return when (unit) {
            DistanceUnit.KILOMETERS -> {
                if (distanceMeters < 1000.0) {
                    "${distanceMeters.roundToInt()} m"
                } else {
                    String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)
                }
            }
            DistanceUnit.NAUTICAL_MILES -> {
                if (distanceMeters < 185.0) {
                    "${distanceMeters.roundToInt()} m"
                } else {
                    String.format(Locale.US, "%.2f NM", distanceMeters / METERS_PER_NAUTICAL_MILE)
                }
            }
        }
    }

    fun formatDepth(depthMeters: Double, unit: DepthUnit): String {
        return when (unit) {
            DepthUnit.METERS -> String.format(Locale.US, "%.1f m", depthMeters)
            DepthUnit.FEET -> String.format(Locale.US, "%.1f ft", depthMeters * FEET_PER_METER)
        }
    }

    fun metersPerSecondToKnots(speedMps: Float): Float {
        return (speedMps * 1.943844f).coerceAtLeast(0f)
    }

    fun lowPassAngle(previousDeg: Float, targetDeg: Float, alpha: Float = 0.18f): Float {
        val prevRad = previousDeg * (PI / 180.0)
        val targetRad = targetDeg * (PI / 180.0)
        val sinVal = (1.0 - alpha) * sin(prevRad) + alpha * sin(targetRad)
        val cosVal = (1.0 - alpha) * cos(prevRad) + alpha * cos(targetRad)
        val result = Math.toDegrees(atan2(sinVal, cosVal))
        return (((result % 360.0) + 360.0) % 360.0).toFloat()
    }
}
