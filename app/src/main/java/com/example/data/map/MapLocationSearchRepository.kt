package com.example.data.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SearchedLocation(
    val id: String,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val suggestedZoom: Double,
    val sourceLabel: String
)

private data class PresetCoastalHub(
    val id: String,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val suggestedZoom: Double,
    val keywords: String
)

/**
 * Global marine & coastal location search combining:
 * 1) Instant lat/lon coordinate parsing (e.g. "1.150, 104.614")
 * 2) Offline curated Kepri & Indonesian fishing coastal hubs + BintanReefAtlas spots
 * 3) Keyless global Open-Meteo Geocoding API for any island, beach, city, or strait worldwide.
 */
class MapLocationSearchRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val presetHubs = listOf(
        PresetCoastalHub(
            id = "hub_bintan_trikora",
            name = "Pantai Trikora, Pulau Bintan",
            subtitle = "Kepulauan Riau • Karang & Mancing Pinggir",
            latitude = 1.1355,
            longitude = 104.6162,
            suggestedZoom = 12.2,
            keywords = "bintan trikora karang tepi kepri"
        ),
        PresetCoastalHub(
            id = "hub_batam_barelang",
            name = "Batam & Jembatan Barelang",
            subtitle = "Kepulauan Riau • Selat & Karang Barelang",
            latitude = 0.9816,
            longitude = 104.0414,
            suggestedZoom = 11.4,
            keywords = "batam barelang jembatan rempang galang kepri"
        ),
        PresetCoastalHub(
            id = "hub_karimun",
            name = "Tanjung Balai Karimun",
            subtitle = "Kepulauan Riau • Pantai Pelawan & Selat Malaka",
            latitude = 1.0155,
            longitude = 103.4180,
            suggestedZoom = 11.4,
            keywords = "karimun tanjung balai pelawan pongkar kundur kepri"
        ),
        PresetCoastalHub(
            id = "hub_lingga",
            name = "Pulau Lingga & Senayang",
            subtitle = "Kepulauan Riau • Karang Benan & Daik Lingga",
            latitude = -0.1850,
            longitude = 104.6200,
            suggestedZoom = 10.6,
            keywords = "lingga daik senayang benan singkep dabo kepri"
        ),
        PresetCoastalHub(
            id = "hub_anambas",
            name = "Kepulauan Anambas (Tarempa)",
            subtitle = "Kepulauan Riau • Terumbu Karang Laut Natuna",
            latitude = 3.2145,
            longitude = 106.2185,
            suggestedZoom = 11.2,
            keywords = "anambas tarempa letung jemaja kepri"
        ),
        PresetCoastalHub(
            id = "hub_natuna",
            name = "Pulau Natuna (Ranai)",
            subtitle = "Kepulauan Riau • Batu Sindu & Karang Laut Natuna",
            latitude = 3.9450,
            longitude = 108.3850,
            suggestedZoom = 11.0,
            keywords = "natuna ranai bunguran sindu kepri"
        ),
        PresetCoastalHub(
            id = "hub_kep_seribu",
            name = "Kepulauan Seribu, Jakarta",
            subtitle = "DKI Jakarta • Karang Pramuka, Harapan & Tidung",
            latitude = -5.7350,
            longitude = 106.5900,
            suggestedZoom = 11.2,
            keywords = "jakarta seribu pramuka tidung pari ancol muara baru"
        ),
        PresetCoastalHub(
            id = "hub_bali_benoa",
            name = "Tanjung Benoa & Nusa Penida, Bali",
            subtitle = "Bali • Karang Timur & Selat Badung",
            latitude = -8.7450,
            longitude = 115.2350,
            suggestedZoom = 11.2,
            keywords = "bali benoa sanur nusa penida amed tulamben"
        ),
        PresetCoastalHub(
            id = "hub_lombok",
            name = "Lombok Selatan & Gili",
            subtitle = "NTB • Kuta Mandalika & Selat Lombok",
            latitude = -8.8950,
            longitude = 116.2800,
            suggestedZoom = 11.0,
            keywords = "lombok gili mandalika kuta senggigi"
        ),
        PresetCoastalHub(
            id = "hub_makassar",
            name = "Perairan Makassar & Spermonde",
            subtitle = "Sulawesi Selatan • Gugusan Karang Samalona",
            latitude = -5.1250,
            longitude = 119.3400,
            suggestedZoom = 11.2,
            keywords = "makassar spermonde samalona kodingareng sulawesi"
        )
    )

    fun quickSuggestions(): List<SearchedLocation> {
        return presetHubs.take(6).map { hub ->
            SearchedLocation(
                id = hub.id,
                name = hub.name,
                subtitle = hub.subtitle,
                latitude = hub.latitude,
                longitude = hub.longitude,
                suggestedZoom = hub.suggestedZoom,
                sourceLabel = "Wilayah"
            )
        }
    }

    suspend fun searchLocations(rawQuery: String): List<SearchedLocation> = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return@withContext quickSuggestions()

        val results = mutableListOf<SearchedLocation>()

        // 1. Direct coordinate parser (works completely offline)
        parseCoordinates(query)?.let { (lat, lon) ->
            results.add(
                SearchedLocation(
                    id = "coords_${lat}_${lon}",
                    name = String.format(Locale.US, "Koordinat %.4f°, %.4f°", lat, lon),
                    subtitle = "Lompat langsung ke titik koordinat GPS ini",
                    latitude = lat,
                    longitude = lon,
                    suggestedZoom = 14.2,
                    sourceLabel = "Koordinat"
                )
            )
        }

        // 2. Match curated spots from BintanReefAtlas
        val lower = query.lowercase(Locale.US)
        BintanReefAtlas.hotspots
            .filter { spot ->
                spot.name.lowercase(Locale.US).contains(lower) ||
                    spot.area.lowercase(Locale.US).contains(lower) ||
                    spot.targetFish.lowercase(Locale.US).contains(lower)
            }
            .take(5)
            .forEach { spot ->
                results.add(
                    SearchedLocation(
                        id = "atlas_${spot.id}",
                        name = spot.name,
                        subtitle = "${spot.area} • ${spot.bottomType.shortTag} (${spot.depthRange})",
                        latitude = spot.latitude,
                        longitude = spot.longitude,
                        suggestedZoom = 14.5,
                        sourceLabel = "Spot Gacor"
                    )
                )
            }

        // 3. Match coastal Indonesian presets
        presetHubs
            .filter { hub ->
                hub.name.lowercase(Locale.US).contains(lower) ||
                    hub.keywords.contains(lower)
            }
            .take(4)
            .forEach { hub ->
                results.add(
                    SearchedLocation(
                        id = hub.id,
                        name = hub.name,
                        subtitle = hub.subtitle,
                        latitude = hub.latitude,
                        longitude = hub.longitude,
                        suggestedZoom = hub.suggestedZoom,
                        sourceLabel = "Wilayah"
                    )
                )
            }

        // 4. Online Global Geocoding (Open-Meteo Geocoding API — free, keyless, worldwide)
        if (query.length >= 2) {
            val remote = fetchRemoteGeocoding(query)
            remote.forEach { item ->
                val duplicate = results.any { existing ->
                    kotlin.math.abs(existing.latitude - item.latitude) < 0.015 &&
                        kotlin.math.abs(existing.longitude - item.longitude) < 0.015
                }
                if (!duplicate) {
                    results.add(item)
                }
            }
        }

        results.take(12)
    }

    private fun fetchRemoteGeocoding(query: String): List<SearchedLocation> {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=id&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BintanSonarAI/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val arr = root.optJSONArray("results") ?: return emptyList()
                val list = ArrayList<SearchedLocation>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val lat = obj.optDouble("latitude", Double.NaN)
                    val lon = obj.optDouble("longitude", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue

                    val admin1 = obj.optString("admin1", "").trim()
                    val country = obj.optString("country", "").trim()
                    val subtitle = listOf(admin1, country)
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                        .ifEmpty { String.format(Locale.US, "%.4f°, %.4f°", lat, lon) }

                    list.add(
                        SearchedLocation(
                            id = "geo_${obj.optLong("id", i.toLong())}",
                            name = name,
                            subtitle = subtitle,
                            latitude = lat,
                            longitude = lon,
                            suggestedZoom = 12.0,
                            sourceLabel = "Global"
                        )
                    )
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    private fun parseCoordinates(raw: String): Pair<Double, Double>? {
        val normalized = raw.replace(',', ' ').trim()
        val parts = normalized.split(Regex("\\s+"))
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -85.0..85.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }
}
