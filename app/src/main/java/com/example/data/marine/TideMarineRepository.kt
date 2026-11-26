package com.example.data.marine

import android.content.Context
import com.example.sensors.MarineGeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
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
    val heightMeters: Double,
    val hourFraction: Float = hourOfDay.toFloat()
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
    val coordinatesLabel: String,
    val sevenDayCalendar: List<DayTideAndSolunar> = emptyList(),
    val fetchedAtLabel: String = "",
    val datumNote: String = "Ref: MSL (0m = rata-rata laut) • Grid laut ~8 km"
)

private data class ParsedMarineSeries(
    val dateKeys: List<String>,
    val points: List<TideHourPoint>,
    val gridLat: Double,
    val gridLon: Double,
    val hasValidSeaLevel: Boolean
)

/**
 * Fetches live 7-day sea-level tide curves, wave height, current speed, and sea surface temperature
 * from Open-Meteo Marine API (free, no API key) using explicit sea-cell selection + automatic
 * nearest-sea probing for inland/shoreline users, backed by a persistent 7-day live offline cache
 * and a J2000-anchored astronomical harmonic fallback when offline with no cached forecast.
 */
class TideMarineRepository(
    context: Context? = null
) {
    private val appContext = context?.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cacheFile: File?
        get() = appContext?.let { File(it.filesDir, CACHE_FILE_NAME) }

    suspend fun fetchTideForecast(
        latitude: Double,
        longitude: Double
    ): TideForecastData = withContext(Dispatchers.IO) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentFraction = (currentHour + currentMinute / 60f).coerceIn(0f, 23.98f)
        val todayDateKey = formatLocalDateKey(now)
        val fetchedAtLabel = formatLocalTimeLabel(now)
        val coordLabel = String.format(Locale.US, "%.4f°, %.4f°", latitude, longitude)

        // 1. Try LIVE Open-Meteo Marine API with explicit cell_selection=sea + nearby sea probe if on land
        val liveFetch = fetchLiveMarineWithSeaProbe(latitude, longitude)
        var usedCachedLive = false
        var activeSeries: ParsedMarineSeries? = liveFetch?.series
        var snappedKm: Double? = liveFetch?.snappedDistanceKm

        if (liveFetch != null && liveFetch.series.hasValidSeaLevel) {
            saveLiveCache(
                rawJson = liveFetch.rawJson,
                reqLat = latitude,
                reqLon = longitude,
                snappedKm = liveFetch.snappedDistanceKm
            )
        } else {
            // 2. If offline or API unreachable, load persistent 7-day LIVE Open-Meteo cache if it covers today
            val cached = loadValidLiveCache(latitude, longitude, todayDateKey)
            if (cached != null) {
                activeSeries = cached.series
                snappedKm = cached.snappedDistanceKm
                usedCachedLive = true
            }
        }

        val hasLiveSeaLevel = activeSeries != null && activeSeries.hasValidSeaLevel
        val todaySlice = activeSeries?.let { extractDaySlice(it, todayDateKey) }
        val tomorrowDateKey = formatLocalDateKey(
            (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        )
        val tomorrowFirstPoint = activeSeries?.let { extractDaySlice(it, tomorrowDateKey)?.firstOrNull() }

        val todayHasValidSeaLevel = hasLiveSeaLevel &&
            todaySlice != null &&
            todaySlice.size >= 24 &&
            todaySlice.any { abs(it.seaLevelMeters) > 0.005 }

        val todayPoints = if (todayHasValidSeaLevel && todaySlice != null) {
            todaySlice
        } else {
            buildAstronomicalHarmonicFallback(
                latitude = latitude,
                longitude = longitude,
                dayCal = now,
                liveWavePoints = todaySlice
            )
        }

        // Build full 7-day Solunar & Tide calendar aligned by calendar date
        val sevenDayCalendar = (0 until 7).map { dayOffset ->
            val dayCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val dateKey = formatLocalDateKey(dayCal)
            val daySlice = activeSeries?.let { extractDaySlice(it, dateKey) }
            val dayHasSeaLevel = hasLiveSeaLevel &&
                daySlice != null &&
                daySlice.size >= 24 &&
                daySlice.any { abs(it.seaLevelMeters) > 0.005 }

            val dayPoints = if (dayHasSeaLevel && daySlice != null) {
                daySlice
            } else {
                buildAstronomicalHarmonicFallback(
                    latitude = latitude,
                    longitude = longitude,
                    dayCal = dayCal,
                    liveWavePoints = daySlice
                )
            }
            SolunarCalendarEngine.buildSolunarSummaryForDay(
                dayCal = dayCal,
                dayIndex = dayOffset,
                longitudeDeg = longitude,
                hourlyPoints = dayPoints
            )
        }

        val isLive = todayHasValidSeaLevel && !usedCachedLive
        val snapSuffix = snappedKm?.takeIf { it >= 1.5 }?.let {
            String.format(Locale.US, " • Sel laut %.1f km", it)
        } ?: ""

        val sourceLabel = when {
            isLive -> "LIVE OPEN-METEO MARINE$snapSuffix"
            todayHasValidSeaLevel && usedCachedLive -> "CACHE LIVE OPEN-METEO (OFFLINE)$snapSuffix"
            else -> "ESTIMASI HARMONIK OFFLINE (PERLU SINYAL)"
        }

        val datumNote = when {
            isLive || (todayHasValidSeaLevel && usedCachedLive) ->
                "Ref: MSL (0m = rata-rata laut) • Selisih pantai sempit ±15–45 mnt"
            else ->
                "Ref: Estimasi astronomi J2000 (offline) • Hubungkan internet untuk live"
        }

        assembleForecast(
            points = todayPoints,
            nextDayHour0 = tomorrowFirstPoint,
            currentFraction = currentFraction,
            isLiveApiData = todayHasValidSeaLevel,
            sourceLabel = sourceLabel,
            coordinatesLabel = coordLabel,
            sevenDayCalendar = sevenDayCalendar,
            fetchedAtLabel = fetchedAtLabel,
            datumNote = datumNote
        )
    }

    private data class LiveMarineFetchResult(
        val rawJson: String,
        val series: ParsedMarineSeries,
        val snappedDistanceKm: Double
    )

    private fun fetchLiveMarineWithSeaProbe(
        latitude: Double,
        longitude: Double
    ): LiveMarineFetchResult? {
        // First attempt at exact requested coordinate with explicit cell_selection=sea
        val direct = runCatching { fetchOpenMeteoMarineRaw(latitude, longitude) }.getOrNull()
        if (direct != null && direct.series.hasValidSeaLevel) {
            val gridDistKm = MarineGeoUtils.distanceMeters(
                latitude,
                longitude,
                direct.series.gridLat,
                direct.series.gridLon
            ) / 1000.0
            return direct.copy(snappedDistanceKm = gridDistKm)
        }

        // If user is standing inland (e.g. Tanjungpinang / Bintan center / Batam interior),
        // probe nearby surrounding ocean cells (~10 km and ~18 km offsets) for live sea-level data.
        val probeOffsets = listOf(
            0.09 to 0.0,    // North (Selat Singapura / Laut Natuna)
            0.0 to 0.09,    // East (Pantai Trikora / Laut Cina Selatan)
            0.0 to -0.09,   // West (Selat Riau / Batam)
            -0.09 to 0.0,   // South (Selat Kijang / Lingga)
            0.16 to 0.08,   // NE offshore
            -0.16 to -0.08  // SW offshore
        )
        for ((dLat, dLon) in probeOffsets) {
            val probeLat = (latitude + dLat).coerceIn(-80.0, 80.0)
            val probeLon = (longitude + dLon).coerceIn(-180.0, 180.0)
            val probed = runCatching { fetchOpenMeteoMarineRaw(probeLat, probeLon) }.getOrNull()
            if (probed != null && probed.series.hasValidSeaLevel) {
                val distKm = MarineGeoUtils.distanceMeters(
                    latitude,
                    longitude,
                    probed.series.gridLat,
                    probed.series.gridLon
                ) / 1000.0
                return probed.copy(snappedDistanceKm = distKm)
            }
        }
        return direct
    }

    private fun fetchOpenMeteoMarineRaw(
        latitude: Double,
        longitude: Double
    ): LiveMarineFetchResult? {
        val url = String.format(
            Locale.US,
            "https://marine-api.open-meteo.com/v1/marine?latitude=%.5f&longitude=%.5f" +
                "&hourly=sea_level_height_msl,wave_height,wave_period,wave_direction,ocean_current_velocity,sea_surface_temperature" +
                "&cell_selection=sea&timezone=auto&forecast_days=7",
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
            val parsed = parseOpenMeteoMarineJson(body, latitude, longitude) ?: return null
            val distKm = MarineGeoUtils.distanceMeters(
                latitude,
                longitude,
                parsed.gridLat,
                parsed.gridLon
            ) / 1000.0
            return LiveMarineFetchResult(
                rawJson = body,
                series = parsed,
                snappedDistanceKm = distKm
            )
        }
    }

    private fun parseOpenMeteoMarineJson(
        body: String,
        fallbackLat: Double,
        fallbackLon: Double
    ): ParsedMarineSeries? {
        val root = JSONObject(body)
        val hourly = root.optJSONObject("hourly") ?: return null
        val gridLat = root.optDouble("latitude", fallbackLat)
        val gridLon = root.optDouble("longitude", fallbackLon)

        val times = hourly.optJSONArray("time") ?: return null
        val seaLevels = hourly.optJSONArray("sea_level_height_msl")
        val waveHeights = hourly.optJSONArray("wave_height")
        val wavePeriods = hourly.optJSONArray("wave_period")
        val waveDirs = hourly.optJSONArray("wave_direction")
        val currents = hourly.optJSONArray("ocean_current_velocity")
        val temps = hourly.optJSONArray("sea_surface_temperature")

        val count = minOf(168, times.length())
        if (count < 24) return null

        val dateKeys = ArrayList<String>(count)
        val list = ArrayList<TideHourPoint>(count)
        var nonNullSeaCount = 0
        var hasNonZeroSea = false

        for (i in 0 until count) {
            val timeStr = times.optString(i, "")
            val dateKey = if (timeStr.length >= 10) timeStr.substring(0, 10) else ""
            val hourFromStr = if (timeStr.length >= 13) {
                timeStr.substring(11, 13).toIntOrNull() ?: (i % 24)
            } else {
                i % 24
            }
            val hourLabel = if (timeStr.length >= 16) {
                timeStr.substring(11, 16)
            } else {
                String.format(Locale.US, "%02d:00", hourFromStr)
            }

            val rawSeaLevel = seaLevels?.takeIf { !it.isNull(i) }?.optDouble(i, Double.NaN)
            val seaLevel = if (rawSeaLevel != null && !rawSeaLevel.isNaN()) {
                nonNullSeaCount++
                if (abs(rawSeaLevel) > 0.005) hasNonZeroSea = true
                rawSeaLevel
            } else {
                0.0
            }

            val waveH = waveHeights?.takeIf { !it.isNull(i) }?.optDouble(i, 0.5) ?: 0.5
            val waveP = wavePeriods?.takeIf { !it.isNull(i) }?.optDouble(i, 5.0) ?: 5.0
            val waveDir = (waveDirs?.takeIf { !it.isNull(i) }?.optDouble(i, 45.0) ?: 45.0).toFloat()
            val currentMps = currents?.takeIf { !it.isNull(i) }?.optDouble(i, 0.35) ?: 0.35
            val currentKn = (currentMps * 1.943844).coerceAtLeast(0.0)
            val tempC = temps?.takeIf { !it.isNull(i) }?.optDouble(i, 29.0) ?: 29.0

            dateKeys.add(dateKey)
            list.add(
                TideHourPoint(
                    hourOfDay = hourFromStr,
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

        return ParsedMarineSeries(
            dateKeys = dateKeys,
            points = list,
            gridLat = gridLat,
            gridLon = gridLon,
            hasValidSeaLevel = nonNullSeaCount >= 24 && hasNonZeroSea
        )
    }

    private fun extractDaySlice(
        series: ParsedMarineSeries,
        dateKey: String
    ): List<TideHourPoint>? {
        val matched = ArrayList<TideHourPoint>(24)
        for (i in series.points.indices) {
            if (series.dateKeys[i] == dateKey) {
                matched.add(series.points[i])
            }
        }
        return if (matched.size >= 24) matched.take(24) else null
    }

    private fun saveLiveCache(
        rawJson: String,
        reqLat: Double,
        reqLon: Double,
        snappedKm: Double
    ) {
        val file = cacheFile ?: return
        runCatching {
            val wrapper = JSONObject().apply {
                put("fetchedAtMs", System.currentTimeMillis())
                put("reqLat", reqLat)
                put("reqLon", reqLon)
                put("snappedKm", snappedKm)
                put("payload", rawJson)
            }
            file.writeText(wrapper.toString())
        }
    }

    private fun loadValidLiveCache(
        latitude: Double,
        longitude: Double,
        todayDateKey: String
    ): LiveMarineFetchResult? {
        val file = cacheFile ?: return null
        if (!file.exists()) return null
        return runCatching {
            val wrapper = JSONObject(file.readText())
            val reqLat = wrapper.optDouble("reqLat", Double.NaN)
            val reqLon = wrapper.optDouble("reqLon", Double.NaN)
            val snappedKm = wrapper.optDouble("snappedKm", 0.0)
            val payload = wrapper.optString("payload", "")
            if (payload.isBlank() || reqLat.isNaN() || reqLon.isNaN()) return null

            // Only reuse cached live tide if within 65 km of the cached coastal region
            val distKm = MarineGeoUtils.distanceMeters(latitude, longitude, reqLat, reqLon) / 1000.0
            if (distKm > 65.0) return null

            val parsed = parseOpenMeteoMarineJson(payload, reqLat, reqLon) ?: return null
            val todaySlice = extractDaySlice(parsed, todayDateKey) ?: return null
            if (!parsed.hasValidSeaLevel || todaySlice.none { abs(it.seaLevelMeters) > 0.005 }) {
                return null
            }
            LiveMarineFetchResult(
                rawJson = payload,
                series = parsed,
                snappedDistanceKm = maxOf(snappedKm, distKm)
            )
        }.getOrNull()
    }

    /**
     * Astronomical mixed semi-diurnal tidal harmonic estimate anchored to J2000.0 UTC
     * (2000-01-01 00:00:00 UTC) so lunar/solar constituent phases stay continuous across years.
     * Calibrated for Bintan / Singapore Strait (M2, S2, K1, O1 constituents) — used ONLY when
     * offline with no cached live Open-Meteo 7-day forecast.
     */
    private fun buildAstronomicalHarmonicFallback(
        latitude: Double,
        longitude: Double,
        dayCal: Calendar,
        liveWavePoints: List<TideHourPoint>?
    ): List<TideHourPoint> {
        val startOfDayCal = (dayCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val spatialPhaseDeg = (longitude - 104.5) * 4.5 + (latitude - 1.1) * 3.0

        return (0..23).map { hour ->
            val sampleMs = startOfDayCal.timeInMillis + hour * 3_600_000L
            val tHoursUtc = (sampleMs - J2000_UTC_MS) / 3_600_000.0

            // Standard astronomical equilibrium arguments at J2000.0 UTC (degrees)
            // minus Bintan / Singapore Strait Greenwich phase lags (degrees)
            val m2Deg = (28.9841042 * tHoursUtc + 200.5 - (298.0 + spatialPhaseDeg))
            val s2Deg = (30.0000000 * tHoursUtc + 0.0 - (345.0 + spatialPhaseDeg))
            val k1Deg = (15.0410686 * tHoursUtc + 10.5 - (108.0 + spatialPhaseDeg * 0.5))
            val o1Deg = (13.9430356 * tHoursUtc + 190.0 - (58.0 + spatialPhaseDeg * 0.5))

            val m2Rad = Math.toRadians(m2Deg)
            val s2Rad = Math.toRadians(s2Deg)
            val k1Rad = Math.toRadians(k1Deg)
            val o1Rad = Math.toRadians(o1Deg)

            val height = 0.72 * cos(m2Rad) +
                0.28 * cos(s2Rad) +
                0.31 * cos(k1Rad) +
                0.29 * cos(o1Rad)

            val slope = abs(
                -0.72 * Math.toRadians(28.9841042) * sin(m2Rad) -
                    0.28 * Math.toRadians(30.0) * sin(s2Rad) -
                    0.31 * Math.toRadians(15.0410686) * sin(k1Rad)
            )
            val estCurrentKn = (0.25 + slope * 2.0).coerceIn(0.2, 2.4)
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
        nextDayHour0: TideHourPoint?,
        currentFraction: Float,
        isLiveApiData: Boolean,
        sourceLabel: String,
        coordinatesLabel: String,
        sevenDayCalendar: List<DayTideAndSolunar>,
        fetchedAtLabel: String,
        datumNote: String
    ): TideForecastData {
        val sample = interpolateAtHour(points, currentFraction)
        val p0 = points[currentFraction.toInt().coerceIn(0, points.lastIndex)]

        // Detect local maxima (High Tide) and local minima (Low Tide) with sub-hour quadratic interpolation
        val extended = if (nextDayHour0 != null) points + nextDayHour0 else points
        val extremes = mutableListOf<TideExtremeEvent>()
        for (i in 1 until extended.lastIndex) {
            val prev = extended[i - 1].seaLevelMeters
            val curr = extended[i].seaLevelMeters
            val next = extended[i + 1].seaLevelMeters

            val isPeak = curr >= prev && curr > next
            val isTrough = curr <= prev && curr < next
            if (isPeak || isTrough) {
                val denom = prev - 2.0 * curr + next
                val delta = if (abs(denom) > 1e-6) {
                    (0.5 * (prev - next) / denom).coerceIn(-0.48, 0.48)
                } else {
                    0.0
                }
                val refinedHour = (i + delta).toFloat()
                if (refinedHour < 0f || refinedHour >= 24f) continue

                val refinedHeight = curr - 0.25 * (prev - next) * delta
                extremes.add(
                    TideExtremeEvent(
                        isHighTide = isPeak,
                        timeLabel = formatHourFraction(refinedHour),
                        hourOfDay = refinedHour.toInt().coerceIn(0, 23),
                        heightMeters = refinedHeight,
                        hourFraction = refinedHour.coerceIn(0f, 23.95f)
                    )
                )
            }
        }

        // Ensure at least highest & lowest points are listed if curve is monotonic
        if (extremes.isEmpty() && points.isNotEmpty()) {
            val maxPt = points.maxBy { it.seaLevelMeters }
            val minPt = points.minBy { it.seaLevelMeters }
            extremes.add(
                TideExtremeEvent(
                    isHighTide = true,
                    timeLabel = maxPt.label,
                    hourOfDay = maxPt.hourOfDay,
                    heightMeters = maxPt.seaLevelMeters,
                    hourFraction = maxPt.hourOfDay.toFloat()
                )
            )
            extremes.add(
                TideExtremeEvent(
                    isHighTide = false,
                    timeLabel = minPt.label,
                    hourOfDay = minPt.hourOfDay,
                    heightMeters = minPt.seaLevelMeters,
                    hourFraction = minPt.hourOfDay.toFloat()
                )
            )
            extremes.sortBy { it.hourFraction }
        }

        return TideForecastData(
            hourlyPoints = points,
            extremes = extremes,
            currentHourFraction = currentFraction,
            currentHeightMeters = sample.heightMeters,
            currentTrend = sample.trend,
            rateCmPerHour = sample.rateCmPerHour,
            currentWaveMeters = p0.waveHeightMeters,
            currentWavePeriodSec = p0.wavePeriodSec,
            currentWaveDirection = MarineGeoUtils.cardinalDirection(p0.waveDirectionDeg),
            currentSpeedKnots = p0.currentKnots,
            seaTempCelsius = p0.seaTempCelsius,
            isLiveApiData = isLiveApiData,
            sourceLabel = sourceLabel,
            coordinatesLabel = coordinatesLabel,
            sevenDayCalendar = sevenDayCalendar,
            fetchedAtLabel = fetchedAtLabel,
            datumNote = datumNote
        )
    }

    private fun formatLocalDateKey(cal: Calendar): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = cal.timeZone
        return fmt.format(cal.time)
    }

    private fun formatLocalTimeLabel(cal: Calendar): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = cal.timeZone
        val tzShort = cal.timeZone.getDisplayName(false, TimeZone.SHORT, Locale.US)
            .let { if (it == "GMT+07:00") "WIB" else if (it == "GMT+08:00") "WITA" else it }
        return "${fmt.format(Date(cal.timeInMillis))} $tzShort"
    }

    companion object {
        private const val CACHE_FILE_NAME = "marine_tide_cache_v1.json"
        // J2000.0 UTC epoch: 2000-01-01 00:00:00 UTC
        private const val J2000_UTC_MS = 946684800000L

        data class InterpolatedTideSample(
            val heightMeters: Double,
            val rateCmPerHour: Double,
            val trend: TideTrend
        )

        fun interpolateAtHour(
            points: List<TideHourPoint>,
            hourFraction: Float
        ): InterpolatedTideSample {
            if (points.isEmpty()) {
                return InterpolatedTideSample(0.0, 0.0, TideTrend.LOW_SLACK)
            }
            val clamped = hourFraction.coerceIn(0f, (points.size - 1).toFloat())
            val idxLow = clamped.toInt().coerceIn(0, points.lastIndex)
            val idxHigh = (idxLow + 1).coerceAtMost(points.lastIndex)
            val frac = (clamped - idxLow).coerceIn(0f, 1f)

            val p0 = points[idxLow]
            val p1 = points[idxHigh]
            val currentHeight = p0.seaLevelMeters + (p1.seaLevelMeters - p0.seaLevelMeters) * frac
            val rateMetersPerHour = if (idxHigh > idxLow) {
                p1.seaLevelMeters - p0.seaLevelMeters
            } else {
                p0.seaLevelMeters - points[(idxLow - 1).coerceAtLeast(0)].seaLevelMeters
            }
            val rateCmPerHour = rateMetersPerHour * 100.0
            val avgHeight = points.map { it.seaLevelMeters }.average()

            val trend = when {
                rateCmPerHour > 3.0 -> TideTrend.RISING
                rateCmPerHour < -3.0 -> TideTrend.FALLING
                currentHeight >= avgHeight -> TideTrend.HIGH_SLACK
                else -> TideTrend.LOW_SLACK
            }
            return InterpolatedTideSample(currentHeight, rateCmPerHour, trend)
        }

        fun formatHourFraction(hourFloat: Float): String {
            val totalMinutes = (hourFloat * 60f).roundToInt().coerceIn(0, 23 * 60 + 59)
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return String.format(Locale.US, "%02d:%02d", h, m)
        }
    }
}
