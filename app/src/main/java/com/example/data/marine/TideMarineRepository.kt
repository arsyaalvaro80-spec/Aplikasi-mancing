package com.example.data.marine

import com.example.sensors.MarineGeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class TideTrend(val label: String) {
    RISING("PASANG NAIK"),
    FALLING("SURUT TURUN"),
    HIGH_SLACK("PUNCAK PASANG"),
    LOW_SLACK("TITIK SURUT")
}

data class TideHourPoint(
    val hourOfDay: Int,
    val label: String,
    val seaLevelMeters: Double,
    val waveHeightMeters: Double,
    val wavePeriodSec: Double,
    val waveDirectionDeg: Float,
    val currentKnots: Double,
    val seaTempCelsius: Double
)

data class TideExtremeEvent(
    val isHighTide: Boolean,
    val timeLabel: String,
    val hourOfDay: Int,
    val heightMeters: Double
)

data class TideForecastData(
    val hourlyPoints: List<TideHourPoint>,
    val extremes: List<TideExtremeEvent>,
    val currentHourFraction: Float,
    val currentHeightMeters: Double,
    val currentTrend: TideTrend,
    val rateCmPerHour: Double,
    val currentWaveMeters: Double,
    val currentWavePeriodSec: Double,
    val currentWaveDirection: String,
    val currentSpeedKnots: Double,
    val seaTempCelsius: Double,
    val isLiveApiData: Boolean,
    val sourceLabel: String,
    val coordinatesLabel: String
)

/**
 * Fetches live 24-hour sea-level tide curves, wave height, current speed, and sea surface temperature
 * from the free Open-Meteo Marine API (no API key required).
 * Falls back gracefully to a multi-constituent astronomical harmonic tide model when offline at sea.
 */
class TideMarineRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTideForecast(
        latitude: Double,
        longitude: Double
    ): TideForecastData = withContext(Dispatchers.IO) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentFraction = (currentHour + currentMinute / 60f).coerceIn(0f, 23.95f)
        val coordLabel = String.format(Locale.US, "%.4f°, %.4f°", latitude, longitude)

        val livePoints = runCatching {
            fetchOpenMeteoMarineHourly(latitude, longitude)
        }.getOrNull()

        val hasLiveSeaLevel = livePoints != null &&
            livePoints.size >= 24 &&
            livePoints.any { abs(it.seaLevelMeters) > 0.005 }

        val points = if (hasLiveSeaLevel && livePoints != null) {
            livePoints.take(24)
        } else {
            // Merge live wave/temp if available, otherwise full Bintan harmonic model
            buildBintanHarmonicFallback(
                latitude = latitude,
                longitude = longitude,
                dayOfYear = now.get(Calendar.DAY_OF_YEAR),
                liveWavePoints = livePoints
            )
        }

        val isLive = hasLiveSeaLevel
        val sourceLabel = if (isLive) {
            "LIVE OPEN-METEO MARINE"
        } else {
            "MODEL HARMONIK BINTAN (OFFLINE)"
        }

        assembleForecast(
            points = points,
            currentFraction = currentFraction,
            isLiveApiData = isLive,
            sourceLabel = sourceLabel,
            coordinatesLabel = coordLabel
        )
    }

    private fun fetchOpenMeteoMarineHourly(
        latitude: Double,
        longitude: Double
    ): List<TideHourPoint>? {
        val url = String.format(
            Locale.US,
            "https://marine-api.open-meteo.com/v1/marine?latitude=%.5f&longitude=%.5f" +
                "&hourly=sea_level_height_msl,wave_height,wave_period,wave_direction,ocean_current_velocity,sea_surface_temperature" +
                "&timezone=auto&forecast_days=2",
            latitude,
            longitude
        )

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "BintanSonarAI/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val hourly = root.optJSONObject("hourly") ?: return null

            val times = hourly.optJSONArray("time") ?: return null
            val seaLevels = hourly.optJSONArray("sea_level_height_msl")
            val waveHeights = hourly.optJSONArray("wave_height")
            val wavePeriods = hourly.optJSONArray("wave_period")
            val waveDirs = hourly.optJSONArray("wave_direction")
            val currents = hourly.optJSONArray("ocean_current_velocity")
            val temps = hourly.optJSONArray("sea_surface_temperature")

            val count = minOf(24, times.length())
            if (count < 24) return null

            val list = ArrayList<TideHourPoint>(24)
            for (i in 0 until count) {
                val timeStr = times.optString(i, "")
                val hourLabel = if (timeStr.length >= 16) timeStr.substring(11, 16) else String.format(Locale.US, "%02d:00", i)
                val seaLevel = seaLevels?.takeIf { !it.isNull(i) }?.optDouble(i, 0.0) ?: 0.0
                val waveH = waveHeights?.takeIf { !it.isNull(i) }?.optDouble(i, 0.5) ?: 0.5
                val waveP = wavePeriods?.takeIf { !it.isNull(i) }?.optDouble(i, 5.0) ?: 5.0
                val waveDir = (waveDirs?.takeIf { !it.isNull(i) }?.optDouble(i, 45.0) ?: 45.0).toFloat()
                val currentMps = currents?.takeIf { !it.isNull(i) }?.optDouble(i, 0.35) ?: 0.35
                val currentKn = (currentMps * 1.943844).coerceAtLeast(0.0)
                val tempC = temps?.takeIf { !it.isNull(i) }?.optDouble(i, 29.0) ?: 29.0

                list.add(
                    TideHourPoint(
                        hourOfDay = i,
                        label = hourLabel,
                        seaLevelMeters = seaLevel,
                        waveHeightMeters = waveH,
                        wavePeriodSec = waveP,
                        waveDirectionDeg = waveDir,
                        currentKnots = currentKn,
                        seaTempCelsius = tempC
                    )
                )
            }
            return list
        }
    }

    /**
     * Astronomical mixed semi-diurnal tidal harmonic model calibrated for Bintan / Singapore Strait
     * (M2 12.42h, S2 12.00h, K1 23.93h, O1 25.82h constituents).
     */
    private fun buildBintanHarmonicFallback(
        latitude: Double,
        longitude: Double,
        dayOfYear: Int,
        liveWavePoints: List<TideHourPoint>?
    ): List<TideHourPoint> {
        val baseHours = dayOfYear * 24.0
        val phaseShift = ((longitude - 104.5) * 0.25 + (latitude - 1.1) * 0.15)
        return (0..23).map { hour ->
            val t = baseHours + hour + phaseShift
            val m2 = 0.78 * sin(2.0 * PI * t / 12.4206 + 0.6)
            val s2 = 0.28 * sin(2.0 * PI * t / 12.0000 + 1.4)
            val k1 = 0.32 * sin(2.0 * PI * t / 23.9345 - 0.5)
            val o1 = 0.22 * sin(2.0 * PI * t / 25.8193 + 0.3)
            val height = (m2 + s2 + k1 + o1)

            // Derivative approximates tidal current velocity
            val slope = abs(
                0.78 * (2.0 * PI / 12.4206) * cos(2.0 * PI * t / 12.4206 + 0.6) +
                    0.28 * (2.0 * PI / 12.0) * cos(2.0 * PI * t / 12.0 + 1.4)
            )
            val estCurrentKn = (0.25 + slope * 2.1).coerceIn(0.2, 2.4)
            val liveWave = liveWavePoints?.getOrNull(hour)

            TideHourPoint(
                hourOfDay = hour,
                label = String.format(Locale.US, "%02d:00", hour),
                seaLevelMeters = height,
                waveHeightMeters = liveWave?.waveHeightMeters ?: (0.45 + 0.2 * abs(sin(hour * 0.35))),
                wavePeriodSec = liveWave?.wavePeriodSec ?: 5.2,
                waveDirectionDeg = liveWave?.waveDirectionDeg ?: 55f,
                currentKnots = liveWave?.currentKnots?.takeIf { it > 0.05 } ?: estCurrentKn,
                seaTempCelsius = liveWave?.seaTempCelsius ?: 29.1
            )
        }
    }

    private fun assembleForecast(
        points: List<TideHourPoint>,
        currentFraction: Float,
        isLiveApiData: Boolean,
        sourceLabel: String,
        coordinatesLabel: String
    ): TideForecastData {
        val idxLow = currentFraction.toInt().coerceIn(0, points.lastIndex)
        val idxHigh = (idxLow + 1).coerceAtMost(points.lastIndex)
        val frac = (currentFraction - idxLow).coerceIn(0f, 1f)

        val p0 = points[idxLow]
        val p1 = points[idxHigh]
        val currentHeight = p0.seaLevelMeters + (p1.seaLevelMeters - p0.seaLevelMeters) * frac
        val rateMetersPerHour = if (idxHigh > idxLow) {
            p1.seaLevelMeters - p0.seaLevelMeters
        } else {
            p0.seaLevelMeters - points[(idxLow - 1).coerceAtLeast(0)].seaLevelMeters
        }
        val rateCmPerHour = rateMetersPerHour * 100.0

        val trend = when {
            rateCmPerHour > 3.0 -> TideTrend.RISING
            rateCmPerHour < -3.0 -> TideTrend.FALLING
            currentHeight >= points.map { it.seaLevelMeters }.average() -> TideTrend.HIGH_SLACK
            else -> TideTrend.LOW_SLACK
        }

        // Detect local maxima (High Tide) and local minima (Low Tide) across the 24-hour curve
        val extremes = mutableListOf<TideExtremeEvent>()
        for (i in 1 until points.lastIndex) {
            val prev = points[i - 1].seaLevelMeters
            val curr = points[i].seaLevelMeters
            val next = points[i + 1].seaLevelMeters
            if (curr >= prev && curr > next) {
                extremes.add(
                    TideExtremeEvent(
                        isHighTide = true,
                        timeLabel = points[i].label,
                        hourOfDay = points[i].hourOfDay,
                        heightMeters = curr
                    )
                )
            } else if (curr <= prev && curr < next) {
                extremes.add(
                    TideExtremeEvent(
                        isHighTide = false,
                        timeLabel = points[i].label,
                        hourOfDay = points[i].hourOfDay,
                        heightMeters = curr
                    )
                )
            }
        }

        // Ensure at least highest & lowest points are listed if curve is monotonic
        if (extremes.isEmpty() && points.isNotEmpty()) {
            val maxPt = points.maxBy { it.seaLevelMeters }
            val minPt = points.minBy { it.seaLevelMeters }
            extremes.add(TideExtremeEvent(true, maxPt.label, maxPt.hourOfDay, maxPt.seaLevelMeters))
            extremes.add(TideExtremeEvent(false, minPt.label, minPt.hourOfDay, minPt.seaLevelMeters))
            extremes.sortBy { it.hourOfDay }
        }

        return TideForecastData(
            hourlyPoints = points,
            extremes = extremes,
            currentHourFraction = currentFraction,
            currentHeightMeters = currentHeight,
            currentTrend = trend,
            rateCmPerHour = rateCmPerHour,
            currentWaveMeters = p0.waveHeightMeters,
            currentWavePeriodSec = p0.wavePeriodSec,
            currentWaveDirection = MarineGeoUtils.cardinalDirection(p0.waveDirectionDeg),
            currentSpeedKnots = p0.currentKnots,
            seaTempCelsius = p0.seaTempCelsius,
            isLiveApiData = isLiveApiData,
            sourceLabel = sourceLabel,
            coordinatesLabel = coordinatesLabel
        )
    }
}
