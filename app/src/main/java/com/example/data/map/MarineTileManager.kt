package com.example.data.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

enum class ChartBaseLayer(
    val id: String,
    val maxZoom: Int
) {
    MARINE_DARK("marine_dark_2x", 18),
    SATELLITE_REEF("satellite_reef", 17),
    OSM_STANDARD("osm_standard", 18)
}

data class TileLoadStatus(
    val loadedCount: Int = 0,
    val activeDownloads: Int = 0,
    val failedCount: Int = 0,
    val lastError: String? = null
)

/**
 * Two-tier (RAM LruCache + app-private disk cache in cacheDir) asynchronous slippy-tile loader.
 * Supports CartoDB Dark Matter @2x retina tiles, Esri World Imagery satellite reefs, OpenStreetMap,
 * and transparent OpenSeaMap nautical seamark overlays with zero API keys and zero storage permissions.
 */
class MarineTileManager(context: Context) {
    private val cacheRoot = File(context.applicationContext.cacheDir, "chart_tiles").apply { mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Keep decoded tiles in memory for smooth 60fps panning
    private val memoryCache = object : LruCache<String, ImageBitmap>(140) {}

    // Track in-flight downloads, empty 404 seamark tiles, and transient failed keys
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())
    private val emptyTiles = Collections.synchronizedSet(mutableSetOf<String>())
    private val failedTiles = Collections.synchronizedSet(mutableSetOf<String>())

    private val _tileVersion = MutableStateFlow(0L)
    val tileVersion: StateFlow<Long> = _tileVersion.asStateFlow()

    private val _loadStatus = MutableStateFlow(TileLoadStatus())
    val loadStatus: StateFlow<TileLoadStatus> = _loadStatus.asStateFlow()

    fun retryFailedTiles() {
        failedTiles.clear()
        _loadStatus.update { it.copy(failedCount = 0, lastError = null) }
        _tileVersion.update { it + 1L }
    }

    fun getBaseTile(layer: ChartBaseLayer, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "${layer.id}_${z}_${x}_${y}"
        memoryCache.get(key)?.let { return it }
        if (emptyTiles.contains(key) || failedTiles.contains(key)) return null

        val url = buildBaseTileUrl(layer, z, x, y)
        requestTileAsync(key = key, layerId = layer.id, z = z, x = x, y = y, url = url, isOverlay = false)
        return null
    }

    fun getOpenSeaMapOverlayTile(z: Int, x: Int, y: Int): ImageBitmap? {
        if (z < 5 || z > 17) return null
        val key = "openseamap_${z}_${x}_${y}"
        memoryCache.get(key)?.let { return it }
        if (emptyTiles.contains(key) || failedTiles.contains(key)) return null

        val url = "https://tiles.openseamap.org/seamark/$z/$x/$y.png"
        requestTileAsync(key = key, layerId = "openseamap", z = z, x = x, y = y, url = url, isOverlay = true)
        return null
    }

    private fun buildBaseTileUrl(layer: ChartBaseLayer, z: Int, x: Int, y: Int): String {
        return when (layer) {
            ChartBaseLayer.MARINE_DARK -> {
                val sub = listOf("a", "b", "c", "d")[((x + y) and 3)]
                // High-DPI @2x retina tiles for razor-sharp island & coastline labels
                "https://$sub.basemaps.cartocdn.com/dark_all/$z/$x/$y@2x.png"
            }
            ChartBaseLayer.SATELLITE_REEF -> {
                // Esri World Imagery uses {z}/{y}/{x} order
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            }
            ChartBaseLayer.OSM_STANDARD -> {
                "https://tile.openstreetmap.org/$z/$x/$y.png"
            }
        }
    }

    private fun requestTileAsync(
        key: String,
        layerId: String,
        z: Int,
        x: Int,
        y: Int,
        url: String,
        isOverlay: Boolean
    ) {
        if (!inFlight.add(key)) return
        _loadStatus.update { it.copy(activeDownloads = it.activeDownloads + 1) }

        scope.launch {
            try {
                val diskFile = File(File(cacheRoot, "$layerId/$z/$x").apply { mkdirs() }, "$y.tile")

                // 1. Check local disk cache first (supports offline use at sea)
                if (diskFile.exists() && diskFile.length() > 0L) {
                    val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                    if (bitmap != null) {
                        memoryCache.put(key, bitmap.asImageBitmap())
                        if (!isOverlay) {
                            _loadStatus.update { it.copy(loadedCount = it.loadedCount + 1, lastError = null) }
                        }
                        _tileVersion.update { it + 1L }
                        return@launch
                    } else {
                        diskFile.delete()
                    }
                }

                // 2. Fetch from tile server with compliant marine User-Agent
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/png,image/jpeg,image/*")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 404 && isOverlay) {
                        emptyTiles.add(key)
                        return@launch
                    }
                    if (!response.isSuccessful) {
                        if (!isOverlay) {
                            failedTiles.add(key)
                            _loadStatus.update {
                                it.copy(
                                    failedCount = it.failedCount + 1,
                                    lastError = "HTTP ${response.code}"
                                )
                            }
                        }
                        return@launch
                    }

                    val bytes = response.body?.bytes() ?: return@launch
                    if (bytes.isEmpty()) {
                        if (isOverlay) emptyTiles.add(key)
                        return@launch
                    }

                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = if (isOverlay) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                    }
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    if (decoded != null) {
                        runCatching { diskFile.writeBytes(bytes) }
                        memoryCache.put(key, decoded.asImageBitmap())
                        if (!isOverlay) {
                            _loadStatus.update { it.copy(loadedCount = it.loadedCount + 1, lastError = null) }
                        }
                        _tileVersion.update { it + 1L }
                    }
                }
            } catch (e: Exception) {
                if (!isOverlay) {
                    failedTiles.add(key)
                    _loadStatus.update {
                        it.copy(
                            failedCount = it.failedCount + 1,
                            lastError = e.localizedMessage ?: "Koneksi offline"
                        )
                    }
                }
            } finally {
                inFlight.remove(key)
                _loadStatus.update {
                    it.copy(activeDownloads = (it.activeDownloads - 1).coerceAtLeast(0))
                }
            }
        }
    }

    companion object {
        private const val USER_AGENT = "BintanSonarAI/1.0 (Android Marine Chartplotter)"
    }
}
