package com.example.data.weather

import com.example.sensors.MarineGeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class WeatherSeverity(val rank: Int, val badgeLabel: String) {
    CLEAR(0, "CERAH • AMAN"),
    CLOUDY(1, "BERAWAN • AMAN"),
    RAIN(2, "HUJAN • WASPADA"),
    HEAVY_RAIN(3, "HUJAN LEBAT"),
    STORM(4, "BADAI PETIR")
}

data class CurrentWeatherInfo(
    val temperatureC: Double,
    val apparentTempC: Double,
    val humidityPercent: Int,
    val cloudCoverPercent: Int,
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    val windGustsKmh: Double,
    val windDirectionDeg: Float,
    val windDirectionCardinal: String,
    val weatherCode: Int,
    val conditionLabel: String,
    val severity: WeatherSeverity
) {
    val windSpeedKnots: Double get() = windSpeedKmh * 0.539957
    val windGustsKnots: Double get() = windGustsKmh * 0.539957
}

data class HourlyWeatherItem(
    val isoTime: String,
    val timeLabel: String,
    val temperatureC: Double,
    val rainProbabilityPercent: Int,
    val precipitationMm: Double,
    val windSpeedKnots: Double,
    val windGustsKnots: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val severity: WeatherSeverity
)

data class DailyWeatherItem(
    val dateIso: String,
    val dayLabel: String,
    val tempMinC: Double,
    val tempMaxC: Double,
    val rainProbabilityMaxPercent: Int,
    val precipitationSumMm: Double,
    val maxWindGustsKnots: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val severity: WeatherSeverity
)

data class RegionalStormZone(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Double,
    val precipitationMm: Double,
    val rainProbabilityNext3hPercent: Int,
    val windGustsKnots: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val severity: WeatherSeverity
)

data class RadarFrameInfo(
    val timestampSec: Long,
    val path: String,
    val timeLabel: String,
    val isForecast: Boolean
)

data class RainRadarMetadata(
    val host: String,
    val frames: List<RadarFrameInfo>,
    val latestFrameIndex: Int
)

data class WeatherDashboardData(
    val current: CurrentWeatherInfo,
    val hourlyNext24h: List<HourlyWeatherItem>,
    val dailyNext7Days: List<DailyWeatherItem>,
    val regionalZones: List<RegionalStormZone>,
    val radarMetadata: RainRadarMetadata?,
    val coordinatesLabel: String,
    val updatedAtLabel: String,
    val safetyHeadline: String,
    val safetyAdvice: String
)

private data class CoastalZoneSeed(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Fetches live current weather, 24-hour hourly forecast, 7-day daily forecast,
 * 7-zone Bintan regional rain/storm status (via free keyless Open-Meteo Forecast API),
 * and live RainViewer global precipitation radar frames (free keyless public API).
 */
class WeatherRadarRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetchWeatherDashboard(
        latitude: Double,
        longitude: Double
    ): Result<WeatherDashboardData> = withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val localForecastDeferred = async { fetchLocalForecast(latitude, longitude) }
                val regionalDeferred = async { runCatching { fetchRegionalStormZones() }.getOrDefault(emptyList()) }
                val radarDeferred = async { runCatching { fetchRainViewerMetadata() }.getOrNull() }

                val (current, hourly24, daily7) = localForecastDeferred.await()
                val regionalZones = regionalDeferred.await()
                val radarMeta = radarDeferred.await()

                val updatedAt = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
                val coordLabel = String.format(Locale.US, "%.4f°, %.4f°", latitude, longitude)
                val (headline, advice) = buildSafetySummary(current, hourly24, regionalZones)

                Result.success(
                    WeatherDashboardData(
                        current = current,
                        hourlyNext24h = hourly24,
                        dailyNext7Days = daily7,
                        regionalZones = regionalZones,
                        radarMetadata = radarMeta,
                        coordinatesLabel = coordLabel,
                        updatedAtLabel = "$updatedAt WIB",
                        safetyHeadline = headline,
                        safetyAdvice = advice
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRainRadarOnly(): RainRadarMetadata? = withContext(Dispatchers.IO) {
        runCatching { fetchRainViewerMetadata() }.getOrNull()
    }

    private fun fetchLocalForecast(
        latitude: Double,
        longitude: Double
    ): Triple<CurrentWeatherInfo, List<HourlyWeatherItem>, List<DailyWeatherItem>> {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_gusts_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_gusts_10m_max" +
                "&timezone=auto&forecast_days=7",
            latitude,
            longitude
        )

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Gagal memuat data cuaca Open-Meteo (HTTP ${response.code}).")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Respons data cuaca kosong.")
            val root = JSONObject(body)

            val currentJson = root.getJSONObject("current")
            val currentTimeIso = currentJson.optString("time", "")
            val currentCode = currentJson.optInt("weather_code", 0)
            val currentPrecip = currentJson.optDouble("precipitation", 0.0)
            val currentGustsKmh = currentJson.optDouble("wind_gusts_10m", 0.0)
            val currentWindDir = currentJson.optDouble("wind_direction_10m", 0.0).toFloat()
            val (condLabel, severity) = decodeWmoWeather(currentCode, currentPrecip, currentGustsKmh)

            val current = CurrentWeatherInfo(
                temperatureC = currentJson.optDouble("temperature_2m", 29.0),
                apparentTempC = currentJson.optDouble("apparent_temperature", 31.0),
                humidityPercent = currentJson.optInt("relative_humidity_2m", 78),
                cloudCoverPercent = currentJson.optInt("cloud_cover", 40),
                precipitationMm = currentPrecip,
                windSpeedKmh = currentJson.optDouble("wind_speed_10m", 8.0),
                windGustsKmh = currentGustsKmh,
                windDirectionDeg = currentWindDir,
                windDirectionCardinal = MarineGeoUtils.cardinalDirection(currentWindDir),
                weatherCode = currentCode,
                conditionLabel = condLabel,
                severity = severity
            )

            val hourlyJson = root.getJSONObject("hourly")
            val hTimes = hourlyJson.getJSONArray("time")
            val hTemps = hourlyJson.getJSONArray("temperature_2m")
            val hRainProb = hourlyJson.getJSONArray("precipitation_probability")
            val hPrecip = hourlyJson.getJSONArray("precipitation")
            val hCodes = hourlyJson.getJSONArray("weather_code")
            val hWind = hourlyJson.getJSONArray("wind_speed_10m")
            val hGusts = hourlyJson.getJSONArray("wind_gusts_10m")

            val startPrefix = if (currentTimeIso.length >= 13) currentTimeIso.substring(0, 13) else ""
            var startIndex = 0
            if (startPrefix.isNotEmpty()) {
                for (i in 0 until hTimes.length()) {
                    if (hTimes.optString(i, "").startsWith(startPrefix)) {
                        startIndex = i
                        break
                    }
                }
            }

            val hourlyList = mutableListOf<HourlyWeatherItem>()
            val endIndex = minOf(startIndex + 24, hTimes.length())
            for (i in startIndex until endIndex) {
                val iso = hTimes.optString(i, "")
                val timeLabel = if (iso.length >= 16) iso.substring(11, 16) else String.format(Locale.US, "%02d:00", i % 24)
                val code = hCodes.optInt(i, 0)
                val precip = hPrecip.optDouble(i, 0.0)
                val gustsKmh = hGusts.optDouble(i, 0.0)
                val (hLabel, hSev) = decodeWmoWeather(code, precip, gustsKmh)
                hourlyList.add(
                    HourlyWeatherItem(
                        isoTime = iso,
                        timeLabel = if (i == startIndex) "Skrg" else timeLabel,
                        temperatureC = hTemps.optDouble(i, 29.0),
                        rainProbabilityPercent = hRainProb.optInt(i, 0),
                        precipitationMm = precip,
                        windSpeedKnots = hWind.optDouble(i, 0.0) * 0.539957,
                        windGustsKnots = gustsKmh * 0.539957,
                        weatherCode = code,
                        conditionLabel = hLabel,
                        severity = hSev
                    )
                )
            }

            val dailyJson = root.getJSONObject("daily")
            val dDates = dailyJson.getJSONArray("time")
            val dCodes = dailyJson.getJSONArray("weather_code")
            val dMaxT = dailyJson.getJSONArray("temperature_2m_max")
            val dMinT = dailyJson.getJSONArray("temperature_2m_min")
            val dPrecipSum = dailyJson.getJSONArray("precipitation_sum")
            val dRainProbMax = dailyJson.getJSONArray("precipitation_probability_max")
            val dGustsMax = dailyJson.getJSONArray("wind_gusts_10m_max")

            val dailyList = mutableListOf<DailyWeatherItem>()
            val parseDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatDay = SimpleDateFormat("EEE, dd MMM", Locale("id", "ID"))

            for (i in 0 until dDates.length()) {
                val isoDate = dDates.optString(i, "")
                val dayLabel = when (i) {
                    0 -> "Hari Ini"
                    1 -> "Besok"
                    else -> runCatching {
                        parseDate.parse(isoDate)?.let { formatDay.format(it) } ?: isoDate
                    }.getOrDefault(isoDate)
                }
                val code = dCodes.optInt(i, 0)
                val precipSum = dPrecipSum.optDouble(i, 0.0)
                val gustsKmh = dGustsMax.optDouble(i, 0.0)
                val (dLabel, dSev) = decodeWmoWeather(code, precipSum / 4.0, gustsKmh)
                dailyList.add(
                    DailyWeatherItem(
                        dateIso = isoDate,
                        dayLabel = dayLabel,
                        tempMinC = dMinT.optDouble(i, 25.0),
                        tempMaxC = dMaxT.optDouble(i, 31.0),
                        rainProbabilityMaxPercent = dRainProbMax.optInt(i, 0),
                        precipitationSumMm = precipSum,
                        maxWindGustsKnots = gustsKmh * 0.539957,
                        weatherCode = code,
                        conditionLabel = dLabel,
                        severity = dSev
                    )
                )
            }

            return Triple(current, hourlyList, dailyList)
        }
    }

    private fun fetchRegionalStormZones(): List<RegionalStormZone> {
        val lats = BINTAN_REGIONAL_ZONES.joinToString(",") { String.format(Locale.US, "%.4f", it.latitude) }
        val lons = BINTAN_REGIONAL_ZONES.joinToString(",") { String.format(Locale.US, "%.4f", it.longitude) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
            "&current=temperature_2m,precipitation,weather_code,wind_gusts_10m" +
            "&hourly=precipitation_probability" +
            "&timezone=auto&forecast_days=1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string()?.trim() ?: return emptyList()
            val itemsArray = if (body.startsWith("[")) {
                JSONArray(body)
            } else {
                JSONArray().put(JSONObject(body))
            }

            val result = mutableListOf<RegionalStormZone>()
            val count = minOf(itemsArray.length(), BINTAN_REGIONAL_ZONES.size)
            for (i in 0 until count) {
                val seed = BINTAN_REGIONAL_ZONES[i]
                val obj = itemsArray.getJSONObject(i)
                val curr = obj.optJSONObject("current") ?: continue
                val currTime = curr.optString("time", "")
                val code = curr.optInt("weather_code", 0)
                val precip = curr.optDouble("precipitation", 0.0)
                val gustsKmh = curr.optDouble("wind_gusts_10m", 0.0)
                val temp = curr.optDouble("temperature_2m", 29.0)

                val next3hProb = extractNext3hRainProb(obj.optJSONObject("hourly"), currTime)
                val (label, severity) = decodeWmoWeather(code, precip, gustsKmh)

                result.add(
                    RegionalStormZone(
                        name = seed.name,
                        latitude = seed.latitude,
                        longitude = seed.longitude,
                        temperatureC = temp,
                        precipitationMm = precip,
                        rainProbabilityNext3hPercent = next3hProb,
                        windGustsKnots = gustsKmh * 0.539957,
                        weatherCode = code,
                        conditionLabel = label,
                        severity = severity
                    )
                )
            }
            // Sort highest storm/rain severity first so alerts jump to top
            return result.sortedWith(
                compareByDescending<RegionalStormZone> { it.severity.rank }
                    .thenByDescending { it.precipitationMm }
                    .thenByDescending { it.rainProbabilityNext3hPercent }
            )
        }
    }

    private fun extractNext3hRainProb(hourly: JSONObject?, currentTimeIso: String): Int {
        if (hourly == null) return 0
        val times = hourly.optJSONArray("time") ?: return 0
        val probs = hourly.optJSONArray("precipitation_probability") ?: return 0
        val prefix = if (currentTimeIso.length >= 13) currentTimeIso.substring(0, 13) else ""
        var startIdx = 0
        if (prefix.isNotEmpty()) {
            for (i in 0 until times.length()) {
                if (times.optString(i, "").startsWith(prefix)) {
                    startIdx = i
                    break
                }
            }
        }
        var maxProb = 0
        for (j in startIdx until minOf(startIdx + 4, probs.length())) {
            maxProb = maxOf(maxProb, probs.optInt(j, 0))
        }
        return maxProb
    }

    private fun fetchRainViewerMetadata(): RainRadarMetadata? {
        val request = Request.Builder()
            .url("https://api.rainviewer.com/public/weather-maps.json")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val host = root.optString("host", "").takeIf(String::isNotBlank) ?: return null
            val radar = root.optJSONObject("radar") ?: return null

            val timeFmt = SimpleDateFormat("HH:mm", Locale("id", "ID"))
            val frames = mutableListOf<RadarFrameInfo>()

            val past = radar.optJSONArray("past")
            if (past != null) {
                val startIdx = (past.length() - 6).coerceAtLeast(0)
                for (i in startIdx until past.length()) {
                    val item = past.optJSONObject(i) ?: continue
                    val ts = item.optLong("time", 0L)
                    val path = item.optString("path", "")
                    if (ts > 0L && path.isNotBlank()) {
                        frames.add(
                            RadarFrameInfo(
                                timestampSec = ts,
                                path = path,
                                timeLabel = timeFmt.format(Date(ts * 1000L)),
                                isForecast = false
                            )
                        )
                    }
                }
            }

            val latestPastIdx = (frames.size - 1).coerceAtLeast(0)

            val nowcast = radar.optJSONArray("nowcast")
            if (nowcast != null) {
                for (i in 0 until minOf(nowcast.length(), 3)) {
                    val item = nowcast.optJSONObject(i) ?: continue
                    val ts = item.optLong("time", 0L)
                    val path = item.optString("path", "")
                    if (ts > 0L && path.isNotBlank()) {
                        frames.add(
                            RadarFrameInfo(
                                timestampSec = ts,
                                path = path,
                                timeLabel = "${timeFmt.format(Date(ts * 1000L))} (Pred)",
                                isForecast = true
                            )
                        )
                    }
                }
            }

            if (frames.isEmpty()) return null
            return RainRadarMetadata(
                host = host,
                frames = frames,
                latestFrameIndex = latestPastIdx
            )
        }
    }

    private fun buildSafetySummary(
        current: CurrentWeatherInfo,
        hourly24: List<HourlyWeatherItem>,
        regionalZones: List<RegionalStormZone>
    ): Pair<String, String> {
        val next6Hours = hourly24.take(6)
        val stormHour = next6Hours.firstOrNull { it.severity == WeatherSeverity.STORM }
        val heavyRainHour = next6Hours.firstOrNull { it.severity == WeatherSeverity.HEAVY_RAIN }
        val stormZones = regionalZones.filter { it.severity.rank >= WeatherSeverity.RAIN.rank }

        return when {
            current.severity == WeatherSeverity.STORM || current.windGustsKnots >= 22.0 -> {
                "BAHAYA BADAI • TUNDA MANCING" to
                    "Terdeteksi badai petir atau hembusan angin kuat (${String.format(Locale.US, "%.0f", current.windGustsKnots)} kn). Hindari area pantai terbuka, batu karang, dan joran karbon."
            }
            stormHour != null -> {
                "WASPADA BADAI SEKITAR ${stormHour.timeLabel}" to
                    "Potensi badai petir terdeteksi dalam 6 jam ke depan (${stormHour.conditionLabel}). Pantau pergerakan awan hujan di Peta Radar Badai."
            }
            current.severity == WeatherSeverity.HEAVY_RAIN || heavyRainHour != null -> {
                "WASPADA HUJAN LEBAT" to
                    "Hujan deras berpotensi melanda area pesisir (${stormZones.size} wilayah Bintan terpantau basah). Siapkan jas hujan dan jaga jarak dari bibir karang licin."
            }
            stormZones.isNotEmpty() -> {
                val names = stormZones.take(2).joinToString(" & ") { it.name.substringBefore(" /") }
                "HUJAN TERPANTAU DI $names" to
                    "Lokasi Anda saat ini relatif aman, namun hujan terdeteksi di $names. Cek Peta Radar Badai untuk melihat arah pergeseran awan."
            }
            else -> {
                "CUACA PESISIR AMAN MANCING" to
                    "Tidak ada badai petir di sekitar Bintan saat ini. Hembusan angin ${String.format(Locale.US, "%.0f", current.windGustsKnots)} knot dari ${current.windDirectionCardinal} — ideal untuk mancing pinggir."
            }
        }
    }

    companion object {
        private const val USER_AGENT = "BintanSonarAI/1.0 (Android Marine Weather Radar)"

        private val BINTAN_REGIONAL_ZONES = listOf(
            CoastalZoneSeed("Lagoi / Bintan Utara", 1.1820, 104.3550),
            CoastalZoneSeed("Berakit / Ujung Utara", 1.2150, 104.5720),
            CoastalZoneSeed("Trikora / Bintan Timur", 1.1480, 104.6480),
            CoastalZoneSeed("Tanjung Uban / Barat", 1.0720, 104.2250),
            CoastalZoneSeed("Tanjung Pinang / Penyengat", 0.9180, 104.4420),
            CoastalZoneSeed("Kijang / Bintan Pesisir", 0.8580, 104.6100),
            CoastalZoneSeed("Nongsa / Selat Bintan", 1.1800, 104.1150)
        )

        fun decodeWmoWeather(
            code: Int,
            precipitationMm: Double,
            windGustsKmh: Double
        ): Pair<String, WeatherSeverity> {
            val base = when (code) {
                0 -> "Cerah" to WeatherSeverity.CLEAR
                1 -> "Cerah Berawan" to WeatherSeverity.CLEAR
                2 -> "Berawan Sebagian" to WeatherSeverity.CLOUDY
                3 -> "Mendung Tebal" to WeatherSeverity.CLOUDY
                45, 48 -> "Berkabut" to WeatherSeverity.CLOUDY
                51, 53 -> "Gerimis Ringan" to WeatherSeverity.RAIN
                55, 56, 57 -> "Gerimis Lebat" to WeatherSeverity.RAIN
                61, 80 -> "Hujan Ringan" to WeatherSeverity.RAIN
                63, 81 -> "Hujan Sedang" to WeatherSeverity.RAIN
                65, 82 -> "Hujan Lebat" to WeatherSeverity.HEAVY_RAIN
                95 -> "Badai Petir" to WeatherSeverity.STORM
                96, 99 -> "Badai Petir Hebat" to WeatherSeverity.STORM
                else -> "Berawan" to WeatherSeverity.CLOUDY
            }
            val boostedSeverity = when {
                base.second == WeatherSeverity.STORM -> WeatherSeverity.STORM
                windGustsKmh >= 42.0 && precipitationMm >= 1.5 -> WeatherSeverity.STORM
                precipitationMm >= 6.0 -> WeatherSeverity.HEAVY_RAIN
                precipitationMm >= 0.4 && base.second.rank < WeatherSeverity.RAIN.rank -> WeatherSeverity.RAIN
                else -> base.second
            }
            return base.first to boostedSeverity
        }
    }
}
