package com.example.data.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.sensors.WebMercator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.floor

enum class ChartBaseLayer(
    val id: String,
    val maxZoom: Int
) {
    // Shares keyless OpenStreetMap tiles rendered through a hue-preserving Marine Dark ColorMatrix
    MARINE_DARK("osm_standard", 18),
    SATELLITE_REEF("satellite_reef", 17),
    OCEAN_BATHYMETRY("ocean_bathymetry", 13),
    OSM_STANDARD("osm_standard", 18)
}

data class TileLoadStatus(
    val loadedCount: Int = 0,
    val activeDownloads: Int = 0,
    val failedCount: Int = 0,
    val lastError: String? = null
)

data class OfflineMapState(
    val isDownloading: Boolean = false,
    val downloadedCount: Int = 0,
    val totalCount: Int = 0,
    val savedTileCount: Int = 0,
    val savedSizeMb: Double = 0.0,
    val statusMessage: String? = null
)

/**
 * Two-tier (RAM LruCache + persistent offline filesDir + app-private cacheDir) asynchronous slippy-tile loader.
 * Uses 100% keyless public tile servers: OpenStreetMap (light + Marine Dark filter),
 * Esri World Imagery satellite reefs, OpenSeaMap seamarks, and RainViewer precipitation radar.
 */
class MarineTileManager(context: Context) {
    private val cacheRoot = File(context.applicationContext.cacheDir, "chart_tiles").apply {
        mkdirs()
        // Remove obsolete CartoDB tile cache that displayed API KEY watermarks
        File(this, "marine_dark_2x").takeIf { it.exists() }?.deleteRecursively()
    }

    // Persistent offline region directory (never auto-purged by Android cache cleaner)
    private val offlineRoot = File(context.applicationContext.filesDir, "offline_chart_tiles").apply {
        mkdirs()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var offlineDownloadJob: Job? = null

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

    private val _offlineState = MutableStateFlow(OfflineMapState())
    val offlineState: StateFlow<OfflineMapState> = _offlineState.asStateFlow()

    init {
        refreshOfflineStats()
    }

    fun refreshOfflineStats() {
        scope.launch {
            val (count, bytes) = computeOfflineUsage()
            _offlineState.update {
                it.copy(
                    savedTileCount = count,
                    savedSizeMb = bytes / (1024.0 * 1024.0)
                )
            }
        }
    }

    fun downloadOfflineRegion(
        layer: ChartBaseLayer,
        centerLat: Double,
        centerLon: Double
    ) {
        if (_offlineState.value.isDownloading) return

        val tileCoords = buildOfflineTileList(layer, centerLat, centerLon)
        if (tileCoords.isEmpty()) return

        offlineDownloadJob = scope.launch {
            val completed = AtomicInteger(0)
            val failed = AtomicInteger(0)
            _offlineState.update {
                it.copy(
                    isDownloading = true,
                    downloadedCount = 0,
                    totalCount = tileCoords.size,
                    statusMessage = "Menyiapkan ${tileCoords.size} ubin peta offline..."
                )
            }

            try {
                val chunks = tileCoords.chunked(4)
                for (batch in chunks) {
                    if (!isActive) break
                    coroutineScope {
                        batch.map { (z, x, y) ->
                            async {
                                val targetFile = File(
                                    File(offlineRoot, "${layer.id}/$z/$x").apply { mkdirs() },
                                    "$y.tile"
                                )
                                val ok = if (targetFile.exists() && targetFile.length() > 0L) {
                                    true
                                } else {
                                    downloadSingleTileToFile(
                                        url = buildBaseTileUrl(layer, z, x, y),
                                        targetFile = targetFile
                                    )
                                }
                                if (!ok) failed.incrementAndGet()
                                val done = completed.incrementAndGet()
                                _offlineState.update {
                                    it.copy(
                                        downloadedCount = done,
                                        statusMessage = "Mengunduh $done / ${tileCoords.size} ubin..."
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                }

                val (count, bytes) = computeOfflineUsage()
                val failCount = failed.get()
                val finalMsg = when {
                    !isActive -> "Unduhan peta offline dibatalkan."
                    failCount == 0 -> "Peta offline tersimpan! Siap dipakai tanpa sinyal."
                    failCount < tileCoords.size -> "Tersimpan (${tileCoords.size - failCount}/${tileCoords.size} ubin berhasil)."
                    else -> "Gagal mengunduh peta offline. Periksa koneksi internet."
                }
                failedTiles.clear()
                _tileVersion.update { it + 1L }
                _offlineState.update {
                    it.copy(
                        isDownloading = false,
                        savedTileCount = count,
                        savedSizeMb = bytes / (1024.0 * 1024.0),
                        statusMessage = finalMsg
                    )
                }
            } catch (e: CancellationException) {
                val (count, bytes) = computeOfflineUsage()
                _offlineState.update {
                    it.copy(
                        isDownloading = false,
                        savedTileCount = count,
                        savedSizeMb = bytes / (1024.0 * 1024.0),
                        statusMessage = "Unduhan peta offline dibatalkan."
                    )
                }
                throw e
            }
        }
    }

    fun cancelOfflineDownload() {
        offlineDownloadJob?.cancel()
        offlineDownloadJob = null
    }

    fun clearOfflineTiles() {
        cancelOfflineDownload()
        scope.launch {
            runCatching {
                offlineRoot.listFiles()?.forEach { it.deleteRecursively() }
            }
            _offlineState.value = OfflineMapState(
                savedTileCount = 0,
                savedSizeMb = 0.0,
                statusMessage = "Peta offline dihapus."
            )
        }
    }

    private fun buildOfflineTileList(
        layer: ChartBaseLayer,
        centerLat: Double,
        centerLon: Double
    ): List<Triple<Int, Int, Int>> {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        val maxZ = minOf(layer.maxZoom, 16)
        // Zoom 11-15 cover ~4.5 km radius around spot; Zoom 16 covers ~1.4 km ultra-detail shoreline
        for (z in 11..maxZ) {
            val radiusKm = if (z >= 16) 1.4 else 4.5
            val dLat = radiusKm / 111.32
            val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)
            val dLon = radiusKm / (111.32 * cosLat)

            val numTiles = 1 shl z
            val x1 = floor(WebMercator.lonToWorldX(centerLon - dLon) * numTiles).toInt()
            val x2 = floor(WebMercator.lonToWorldX(centerLon + dLon) * numTiles).toInt()
            val y1 = floor(WebMercator.latToWorldY(centerLat + dLat) * numTiles).toInt().coerceIn(0, numTiles - 1)
            val y2 = floor(WebMercator.latToWorldY(centerLat - dLat) * numTiles).toInt().coerceIn(0, numTiles - 1)

            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)

            for (txRaw in minX..maxX) {
                val tx = ((txRaw % numTiles) + numTiles) % numTiles
                for (ty in minY..maxY) {
                    result.add(Triple(z, tx, ty))
                }
            }
        }
        return result.take(180)
    }

    private fun downloadSingleTileToFile(url: String, targetFile: File): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/png,image/jpeg,image/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val bytes = response.body?.bytes() ?: return false
                if (bytes.isEmpty()) return false
                targetFile.writeBytes(bytes)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun computeOfflineUsage(): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        offlineRoot.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".tile")) {
                count++
                bytes += file.length()
            }
        }
        return count to bytes
    }

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

    /**
     * Fetches transparent live RainViewer precipitation & storm radar tiles (NEXRAD scheme 6).
     * RainViewer free public tiles are guaranteed up to zoom 7; higher map zoom levels crop & scale
     * the exact sub-rect of the zoom 7 tile in ChartplotterScreen so radar clouds never blank out.
     */
    fun getRainRadarOverlayTile(
        host: String,
        framePath: String,
        z: Int,
        x: Int,
        y: Int
    ): ImageBitmap? {
        if (z < 1 || z > RAIN_RADAR_MAX_ZOOM) return null
        val key = "rainradar_${framePath}_${z}_${x}_${y}"
        memoryCache.get(key)?.let { return it }
        if (emptyTiles.contains(key) || failedTiles.contains(key)) return null

        val cleanHost = host.trimEnd('/')
        val url = "$cleanHost$framePath/256/$z/$x/$y/6/1_1.png"
        requestTileAsync(
            key = key,
            layerId = "rainradar",
            z = z,
            x = x,
            y = y,
            url = url,
            isOverlay = true,
            persistToDisk = false
        )
        return null
    }

    private fun buildBaseTileUrl(layer: ChartBaseLayer, z: Int, x: Int, y: Int): String {
        return when (layer) {
            ChartBaseLayer.MARINE_DARK,
            ChartBaseLayer.OSM_STANDARD -> {
                "https://tile.openstreetmap.org/$z/$x/$y.png"
            }
            ChartBaseLayer.SATELLITE_REEF -> {
                // Esri World Imagery uses {z}/{y}/{x} order — shows shallow coral reefs clearly
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            }
            ChartBaseLayer.OCEAN_BATHYMETRY -> {
                // Esri World Ocean Base (keyless public bathymetry & underwater depth contour map)
                "https://server.arcgisonline.com/ArcGIS/rest/services/Ocean/World_Ocean_Base/MapServer/tile/$z/$y/$x"
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
        isOverlay: Boolean,
        persistToDisk: Boolean = true
    ) {
        if (!inFlight.add(key)) return
        _loadStatus.update { it.copy(activeDownloads = it.activeDownloads + 1) }

        scope.launch {
            try {
                val persistentOfflineFile = if (persistToDisk) {
                    File(offlineRoot, "$layerId/$z/$x/$y.tile")
                } else {
                    null
                }
                val diskFile = if (persistToDisk) {
                    File(File(cacheRoot, "$layerId/$z/$x").apply { mkdirs() }, "$y.tile")
                } else {
                    null
                }

                // 1. Check persistent offline pack first, then rolling disk cache (supports 100% offline use at sea)
                val cachedSource = when {
                    persistentOfflineFile != null && persistentOfflineFile.exists() && persistentOfflineFile.length() > 0L -> persistentOfflineFile
                    diskFile != null && diskFile.exists() && diskFile.length() > 0L -> diskFile
                    else -> null
                }
                if (cachedSource != null) {
                    val bitmap = BitmapFactory.decodeFile(cachedSource.absolutePath)
                    if (bitmap != null) {
                        memoryCache.put(key, bitmap.asImageBitmap())
                        if (!isOverlay) {
                            _loadStatus.update { it.copy(loadedCount = it.loadedCount + 1, lastError = null) }
                        }
                        _tileVersion.update { it + 1L }
                        return@launch
                    } else {
                        cachedSource.delete()
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
                        if (diskFile != null) {
                            runCatching { diskFile.writeBytes(bytes) }
                        }
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
        const val RAIN_RADAR_MAX_ZOOM = 7
    }
}
