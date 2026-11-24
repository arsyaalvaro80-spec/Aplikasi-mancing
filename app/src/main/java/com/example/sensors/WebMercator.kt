package com.example.sensors

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class LatLon(
    val latitude: Double,
    val longitude: Double
)

/**
 * Pure Web Mercator (EPSG:3857) projection utilities for Slippy-Tile marine chartplotter rendering.
 */
object WebMercator {
    private const val MAX_LAT = 85.05112878

    fun latToWorldY(latitude: Double): Double {
        val clamped = latitude.coerceIn(-MAX_LAT, MAX_LAT)
        val latRad = Math.toRadians(clamped)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    }

    fun lonToWorldX(longitude: Double): Double {
        return ((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0)
    }

    fun worldYToLat(worldY: Double): Double {
        val clampedY = worldY.coerceIn(0.0, 1.0)
        val n = PI - 2.0 * PI * clampedY
        return Math.toDegrees(atan(sinh(n)))
    }

    fun worldXToLon(worldX: Double): Double {
        val wrapped = ((worldX % 1.0) + 1.0) % 1.0
        return wrapped * 360.0 - 180.0
    }

    fun totalWorldPixels(zoom: Double, baseTilePx: Double): Double {
        return baseTilePx * 2.0.pow(zoom)
    }

    fun latLonToScreen(
        targetLat: Double,
        targetLon: Double,
        centerLat: Double,
        centerLon: Double,
        zoom: Double,
        baseTilePx: Double,
        viewportWidth: Double,
        viewportHeight: Double
    ): Pair<Float, Float> {
        val worldPx = totalWorldPixels(zoom, baseTilePx)
        val centerX = lonToWorldX(centerLon) * worldPx
        val centerY = latToWorldY(centerLat) * worldPx
        val targetX = lonToWorldX(targetLon) * worldPx
        val targetY = latToWorldY(targetLat) * worldPx

        val screenX = viewportWidth / 2.0 + (targetX - centerX)
        val screenY = viewportHeight / 2.0 + (targetY - centerY)
        return screenX.toFloat() to screenY.toFloat()
    }

    fun screenToLatLon(
        screenX: Double,
        screenY: Double,
        centerLat: Double,
        centerLon: Double,
        zoom: Double,
        baseTilePx: Double,
        viewportWidth: Double,
        viewportHeight: Double
    ): LatLon {
        val worldPx = totalWorldPixels(zoom, baseTilePx)
        val centerX = lonToWorldX(centerLon) * worldPx
        val centerY = latToWorldY(centerLat) * worldPx

        val targetWorldX = (centerX + (screenX - viewportWidth / 2.0)) / worldPx
        val targetWorldY = (centerY + (screenY - viewportHeight / 2.0)) / worldPx
        return LatLon(
            latitude = worldYToLat(targetWorldY),
            longitude = worldXToLon(targetWorldX)
        )
    }

    /**
     * Approximate meters represented by 1 screen pixel at the given latitude and zoom.
     */
    fun metersPerPixel(latitude: Double, zoom: Double, baseTilePx: Double): Double {
        val earthCircumference = 40_075_016.686
        val worldPx = totalWorldPixels(zoom, baseTilePx)
        return (earthCircumference * cos(Math.toRadians(latitude))) / worldPx
    }
}
