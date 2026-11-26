package com.example.ui.screens

import android.Manifest
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.WaypointEntity
import com.example.data.map.BintanHotspot
import com.example.data.map.BintanReefAtlas
import com.example.data.map.ChartBaseLayer
import com.example.data.map.MarineTileManager
import com.example.data.map.OfflineMapState
import com.example.data.map.SearchedLocation
import com.example.data.map.SpotAccessType
import com.example.data.preferences.DepthUnit
import com.example.data.preferences.FishingMode
import com.example.data.preferences.SonarSettings
import com.example.data.weather.WeatherSeverity
import com.example.sensors.CompassTelemetry
import com.example.sensors.GpsTelemetry
import com.example.sensors.LatLon
import com.example.sensors.MarineGeoUtils
import com.example.sensors.WebMercator
import com.example.ui.components.BintanSpotAtlasDialog
import com.example.ui.components.MapLocationSearchDialog
import com.example.ui.components.SelectedHotspotDetailCard
import com.example.ui.components.reefColorFor
import com.example.viewmodel.StormMapFocusRequest
import com.example.viewmodel.WeatherUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun ChartplotterScreen(
    tileManager: MarineTileManager,
    gpsTelemetry: GpsTelemetry,
    compassTelemetry: CompassTelemetry,
    settings: SonarSettings,
    waypoints: List<WaypointEntity>,
    selectedWaypoint: WaypointEntity?,
    vesselTrack: List<LatLon>,
    weatherState: WeatherUiState,
    stormMapRequest: StormMapFocusRequest?,
    onConsumeStormMapRequest: () -> Unit,
    onRefreshWeather: () -> Unit,
    onSelectWaypoint: (Long) -> Unit,
    onSaveWaypointAt: (name: String, lat: Double, lon: Double, depthMeters: Double, targetFish: String, notes: String) -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tileVersion by tileManager.tileVersion.collectAsStateWithLifecycle()
    val loadStatus by tileManager.loadStatus.collectAsStateWithLifecycle()
    val offlineState by tileManager.offlineState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    // Keep tile rendering crisp (~384-480px) so Pulau Bintan coastline & labels remain sharp
    val baseTilePx = remember(density) { 256.0 * density.density.coerceIn(1.35f, 1.85f) }

    val isShoreMode = settings.fishingMode == FishingMode.SHORE
    var activeLayer by remember {
        mutableStateOf(
            if (isShoreMode) ChartBaseLayer.SATELLITE_REEF else ChartBaseLayer.MARINE_DARK
        )
    }
    var showOpenSeaMap by remember { mutableStateOf(!isShoreMode) }
    var showCastingRings by remember { mutableStateOf(isShoreMode) }
    var showStormRadar by remember { mutableStateOf(false) }
    var showReefHotspots by remember { mutableStateOf(true) }
    var selectedHotspot by remember { mutableStateOf<BintanHotspot?>(null) }
    var showSpotAtlasDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchedPin by remember { mutableStateOf<SearchedLocation?>(null) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var selectedRadarFrameIdx by remember { mutableStateOf(-1) }
    var followVessel by remember { mutableStateOf(true) }
    var dropPinMode by remember { mutableStateOf(false) }
    var pendingPinCoords by remember { mutableStateOf<LatLon?>(null) }

    var centerLat by remember { mutableDoubleStateOf(gpsTelemetry.latitude) }
    var centerLon by remember { mutableDoubleStateOf(gpsTelemetry.longitude) }
    // Zoom closer when GPS is locked in Shore mode; otherwise frame Pulau Bintan coastline clearly
    var zoom by remember { mutableDoubleStateOf(if (isShoreMode && gpsTelemetry.isLiveFix) 15.2 else 11.2) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val weatherData = (weatherState as? WeatherUiState.Ready)?.data
    val radarMeta = weatherData?.radarMetadata
    val activeRadarFrame = radarMeta?.let { meta ->
        val idx = if (selectedRadarFrameIdx in meta.frames.indices) selectedRadarFrameIdx else meta.latestFrameIndex
        meta.frames.getOrNull(idx)
    }

    LaunchedEffect(radarMeta?.frames?.size) {
        if (radarMeta != null && selectedRadarFrameIdx !in radarMeta.frames.indices) {
            selectedRadarFrameIdx = radarMeta.latestFrameIndex
        }
    }

    LaunchedEffect(stormMapRequest?.id) {
        val req = stormMapRequest ?: return@LaunchedEffect
        showStormRadar = true
        followVessel = false
        if (req.latitude != null && req.longitude != null) {
            centerLat = req.latitude
            centerLon = req.longitude
            zoom = 11.2
        } else {
            // Frame entire Pulau Bintan & Singapore Strait storm field
            centerLat = 1.07
            centerLon = 104.46
            zoom = 9.5
        }
        onConsumeStormMapRequest()
    }

    var previousShoreMode by remember { mutableStateOf(isShoreMode) }
    LaunchedEffect(isShoreMode) {
        if (isShoreMode != previousShoreMode) {
            previousShoreMode = isShoreMode
            if (isShoreMode && !showStormRadar) {
                activeLayer = ChartBaseLayer.SATELLITE_REEF
                showCastingRings = true
                zoom = zoom.coerceAtMost(ChartBaseLayer.SATELLITE_REEF.maxZoom.toDouble())
            } else if (!isShoreMode && !showStormRadar) {
                activeLayer = ChartBaseLayer.MARINE_DARK
                showCastingRings = false
            }
        }
    }

    // Hue-preserving Marine Dark Mode matrix (invert + 180° hue rotation) for keyless OSM tiles:
    // renders oceans deep nautical navy-blue, land dark slate, forests deep green, and labels crisp white.
    val marineDarkFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.528f, -1.316f, -0.132f, 0f, 238f,
                    -0.392f, -0.396f, -0.132f, 0f, 242f,
                    -0.392f, -1.316f, 0.788f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onLocationPermissionResult(granted)
    }

    // Automatically follow live vessel GPS coordinates when follow mode is active
    LaunchedEffect(gpsTelemetry.latitude, gpsTelemetry.longitude, followVessel) {
        if (followVessel) {
            centerLat = gpsTelemetry.latitude
            centerLon = gpsTelemetry.longitude
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface

    val targetDistanceMeters = selectedWaypoint?.let { target ->
        MarineGeoUtils.distanceMeters(
            gpsTelemetry.latitude,
            gpsTelemetry.longitude,
            target.latitude,
            target.longitude
        )
    }
    val targetBearing = selectedWaypoint?.let { target ->
        MarineGeoUtils.bearingDegrees(
            gpsTelemetry.latitude,
            gpsTelemetry.longitude,
            target.latitude,
            target.longitude
        )
    }
    val signedTurn = targetBearing?.let {
        MarineGeoUtils.signedTurnDegrees(it, compassTelemetry.azimuthDegrees)
    }

    val labelTextPaint = remember(textPrimaryColor) {
        Paint().apply {
            isAntiAlias = true
            color = textPrimaryColor.toArgb()
            textSize = 32f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }
    val routeBadgePaint = remember(primaryColor) {
        Paint().apply {
            isAntiAlias = true
            color = primaryColor.toArgb()
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }
    val hotspotLabelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#FFE8B6")
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(7f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }
    val reefZoneLabelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#FF9E80")
            textSize = 27f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Full-bleed interactive Marine Slippy-Tile Chartplotter Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(activeLayer, baseTilePx) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val w = size.width.toDouble()
                        val h = size.height.toDouble()
                        if (w <= 0.0 || h <= 0.0) return@detectTransformGestures

                        if (pan != Offset.Zero) {
                            followVessel = false
                            val worldPx = WebMercator.totalWorldPixels(zoom, baseTilePx)
                            val newWorldX = WebMercator.lonToWorldX(centerLon) - (pan.x / worldPx)
                            val newWorldY = WebMercator.latToWorldY(centerLat) - (pan.y / worldPx)
                            centerLat = WebMercator.worldYToLat(newWorldY)
                            centerLon = WebMercator.worldXToLon(newWorldX)
                        }

                        if (gestureZoom != 1f) {
                            val zoomDelta = ln(gestureZoom.toDouble()) / ln(2.0)
                            zoom = (zoom + zoomDelta).coerceIn(5.0, activeLayer.maxZoom.toDouble())
                        }
                    }
                }
                .pointerInput(waypoints, dropPinMode, showReefHotspots, baseTilePx) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toDouble()
                        val h = size.height.toDouble()
                        if (w <= 0.0 || h <= 0.0) return@detectTapGestures

                        if (dropPinMode) {
                            pendingPinCoords = WebMercator.screenToLatLon(
                                screenX = tapOffset.x.toDouble(),
                                screenY = tapOffset.y.toDouble(),
                                centerLat = centerLat,
                                centerLon = centerLon,
                                zoom = zoom,
                                baseTilePx = baseTilePx,
                                viewportWidth = w,
                                viewportHeight = h
                            )
                            dropPinMode = false
                            return@detectTapGestures
                        }

                        // Hit-test saved waypoints within 44dp radius
                        val hitRadiusPx = with(density) { 44.dp.toPx() }
                        var closest: WaypointEntity? = null
                        var bestDist = Float.MAX_VALUE
                        waypoints.forEach { wp ->
                            val (wx, wy) = WebMercator.latLonToScreen(
                                targetLat = wp.latitude,
                                targetLon = wp.longitude,
                                centerLat = centerLat,
                                centerLon = centerLon,
                                zoom = zoom,
                                baseTilePx = baseTilePx,
                                viewportWidth = w,
                                viewportHeight = h
                            )
                            val d = hypot(tapOffset.x - wx, tapOffset.y - wy)
                            if (d <= hitRadiusPx && d < bestDist) {
                                bestDist = d
                                closest = wp
                            }
                        }
                        if (closest != null) {
                            selectedHotspot = null
                            onSelectWaypoint(closest!!.id)
                            return@detectTapGestures
                        }

                        // Hit-test curated Pulau Bintan fishing hotspots within 44dp radius
                        if (showReefHotspots) {
                            var closestSpot: BintanHotspot? = null
                            var bestSpotDist = Float.MAX_VALUE
                            BintanReefAtlas.hotspots.forEach { spot ->
                                val (hx, hy) = WebMercator.latLonToScreen(
                                    targetLat = spot.latitude,
                                    targetLon = spot.longitude,
                                    centerLat = centerLat,
                                    centerLon = centerLon,
                                    zoom = zoom,
                                    baseTilePx = baseTilePx,
                                    viewportWidth = w,
                                    viewportHeight = h
                                )
                                val d = hypot(tapOffset.x - hx, tapOffset.y - hy)
                                if (d <= hitRadiusPx && d < bestSpotDist) {
                                    bestSpotDist = d
                                    closestSpot = spot
                                }
                            }
                            if (closestSpot != null) {
                                selectedHotspot = closestSpot
                                followVessel = false
                                return@detectTapGestures
                            }
                        }

                        // Tapping open water dismisses hotspot card
                        selectedHotspot = null
                    }
                }
                .testTag("chartplotter_map_canvas")
        ) {
            // Read tileVersion to re-render immediately when tiles arrive from disk/network
            @Suppress("UNUSED_VARIABLE")
            val versionTrigger = tileVersion

            val widthD = size.width.toDouble()
            val heightD = size.height.toDouble()

            // 1. Marine Void Backdrop + Graticule Grid (always crisp even while loading / offline)
            drawRect(color = backgroundColor)
            val gridStep = 72.dp.toPx()
            var gx = 0f
            while (gx < size.width) {
                drawLine(
                    color = gridColor.copy(alpha = 0.25f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(
                    color = gridColor.copy(alpha = 0.25f),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1f
                )
                gy += gridStep
            }

            // 2. Slippy-Tile Rendering (Base Layer + Optional OpenSeaMap Seamarks Overlay)
            val tileZ = floor(zoom).toInt().coerceIn(2, activeLayer.maxZoom)
            val numTiles = 1 shl tileZ
            val worldPx = WebMercator.totalWorldPixels(zoom, baseTilePx)
            val tileSpanPx = worldPx / numTiles.toDouble()
            val centerPxX = WebMercator.lonToWorldX(centerLon) * worldPx
            val centerPxY = WebMercator.latToWorldY(centerLat) * worldPx

            val minTileX = floor((centerPxX - widthD / 2.0) / tileSpanPx).toInt() - 1
            val maxTileX = ceil((centerPxX + widthD / 2.0) / tileSpanPx).toInt() + 1
            val minTileY = floor((centerPxY - heightD / 2.0) / tileSpanPx).toInt().coerceAtLeast(0)
            val maxTileY = ceil((centerPxY + heightD / 2.0) / tileSpanPx).toInt().coerceAtMost(numTiles - 1)

            val drawSizePx = ceil(tileSpanPx).toInt() + 1
            val dstSize = IntSize(drawSizePx, drawSizePx)

            for (txRaw in minTileX..maxTileX) {
                val tx = ((txRaw % numTiles) + numTiles) % numTiles
                val leftPx = floor(widthD / 2.0 + (txRaw * tileSpanPx - centerPxX)).toInt()
                for (ty in minTileY..maxTileY) {
                    val topPx = floor(heightD / 2.0 + (ty * tileSpanPx - centerPxY)).toInt()
                    val dstOffset = IntOffset(leftPx, topPx)

                    val baseTile = tileManager.getBaseTile(activeLayer, tileZ, tx, ty)
                    if (baseTile != null) {
                        drawImage(
                            image = baseTile,
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            colorFilter = if (activeLayer == ChartBaseLayer.MARINE_DARK) marineDarkFilter else null
                        )
                    }

                    if (showOpenSeaMap) {
                        val seaTile = tileManager.getOpenSeaMapOverlayTile(tileZ, tx, ty)
                        if (seaTile != null) {
                            drawImage(
                                image = seaTile,
                                dstOffset = dstOffset,
                                dstSize = dstSize
                            )
                        }
                    }

                    // Live RainViewer Precipitation & Storm Radar Overlay (supports native & high-zoom crop)
                    if (showStormRadar && radarMeta != null && activeRadarFrame != null) {
                        val radarZ = tileZ.coerceAtMost(MarineTileManager.RAIN_RADAR_MAX_ZOOM)
                        val zoomDiff = (tileZ - radarZ).coerceAtLeast(0)
                        val scaleFactor = 1 shl zoomDiff
                        val radarX = tx shr zoomDiff
                        val radarY = ty shr zoomDiff
                        val radarTile = tileManager.getRainRadarOverlayTile(
                            host = radarMeta.host,
                            framePath = activeRadarFrame.path,
                            z = radarZ,
                            x = radarX,
                            y = radarY
                        )
                        if (radarTile != null) {
                            if (zoomDiff == 0) {
                                drawImage(
                                    image = radarTile,
                                    dstOffset = dstOffset,
                                    dstSize = dstSize,
                                    alpha = 0.68f
                                )
                            } else {
                                val subX = tx and (scaleFactor - 1)
                                val subY = ty and (scaleFactor - 1)
                                val srcStepW = (radarTile.width.toFloat() / scaleFactor.toFloat())
                                val srcStepH = (radarTile.height.toFloat() / scaleFactor.toFloat())
                                val srcOffset = IntOffset(
                                    x = floor(subX * srcStepW).toInt().coerceIn(0, radarTile.width - 1),
                                    y = floor(subY * srcStepH).toInt().coerceIn(0, radarTile.height - 1)
                                )
                                val srcSize = IntSize(
                                    width = ceil(srcStepW).toInt().coerceAtLeast(1)
                                        .coerceAtMost(radarTile.width - srcOffset.x),
                                    height = ceil(srcStepH).toInt().coerceAtLeast(1)
                                        .coerceAtMost(radarTile.height - srcOffset.y)
                                )
                                drawImage(
                                    image = radarTile,
                                    srcOffset = srcOffset,
                                    srcSize = srcSize,
                                    dstOffset = dstOffset,
                                    dstSize = dstSize,
                                    alpha = 0.68f
                                )
                            }
                        }
                    }
                }
            }

            // 2b. Live Regional Bintan Storm & Rain Zone Markers when Storm Radar is active
            if (showStormRadar && weatherData != null) {
                weatherData.regionalZones.forEach { zone ->
                    val (zx, zy) = WebMercator.latLonToScreen(
                        targetLat = zone.latitude,
                        targetLon = zone.longitude,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom,
                        baseTilePx = baseTilePx,
                        viewportWidth = widthD,
                        viewportHeight = heightD
                    )
                    if (zx in -120f..(size.width + 120f) && zy in -120f..(size.height + 120f)) {
                        val zoneColor = when (zone.severity) {
                            WeatherSeverity.STORM -> errorColor
                            WeatherSeverity.HEAVY_RAIN -> Color(0xFFFF8A3D)
                            WeatherSeverity.RAIN -> tertiaryColor
                            WeatherSeverity.CLOUDY -> secondaryColor
                            WeatherSeverity.CLEAR -> primaryColor
                        }
                        val haloRadius = when (zone.severity) {
                            WeatherSeverity.STORM -> 42.dp.toPx()
                            WeatherSeverity.HEAVY_RAIN -> 34.dp.toPx()
                            WeatherSeverity.RAIN -> 28.dp.toPx()
                            else -> 18.dp.toPx()
                        }
                        val zoneCenter = Offset(zx, zy)
                        drawCircle(
                            color = zoneColor.copy(alpha = if (zone.severity.rank >= WeatherSeverity.RAIN.rank) 0.28f else 0.14f),
                            radius = haloRadius,
                            center = zoneCenter
                        )
                        drawCircle(
                            color = zoneColor.copy(alpha = 0.85f),
                            radius = haloRadius * 0.55f,
                            center = zoneCenter,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = zoneColor,
                            radius = 6.dp.toPx(),
                            center = zoneCenter
                        )
                        val shortName = zone.name.substringBefore(" /")
                        val badgeSuffix = when (zone.severity) {
                            WeatherSeverity.STORM -> "BADAI"
                            WeatherSeverity.HEAVY_RAIN -> "LEBAT ${String.format(Locale.US, "%.1f", zone.precipitationMm)}mm"
                            WeatherSeverity.RAIN -> "HUJAN ${String.format(Locale.US, "%.1f", zone.precipitationMm)}mm"
                            WeatherSeverity.CLOUDY -> "AWAN ${String.format(Locale.US, "%.0f", zone.windGustsKnots)}kn"
                            WeatherSeverity.CLEAR -> "CERAH ${String.format(Locale.US, "%.0f", zone.windGustsKnots)}kn"
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            "$shortName • $badgeSuffix",
                            zx + 10.dp.toPx(),
                            zy - 8.dp.toPx(),
                            labelTextPaint
                        )
                    }
                }
            }

            // 2c. Pulau Bintan Coral Reef & Drop-Off Zones + Popular Fishing Hotspots
            if (showReefHotspots) {
                val mpp = WebMercator.metersPerPixel(centerLat, zoom, baseTilePx)

                // Draw coral reef & rocky drop-off zone footprints
                BintanReefAtlas.reefZones.forEach { zone ->
                    val (rx, ry) = WebMercator.latLonToScreen(
                        targetLat = zone.centerLat,
                        targetLon = zone.centerLon,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom,
                        baseTilePx = baseTilePx,
                        viewportWidth = widthD,
                        viewportHeight = heightD
                    )
                    val radiusPx = (zone.radiusMeters / mpp).toFloat().coerceIn(14.dp.toPx(), 420.dp.toPx())
                    if (rx in (-radiusPx - 40f)..(size.width + radiusPx + 40f) &&
                        ry in (-radiusPx - 40f)..(size.height + radiusPx + 40f)
                    ) {
                        val zoneColor = reefColorFor(zone.bottomType)
                        val zoneCenter = Offset(rx, ry)
                        drawCircle(
                            color = zoneColor.copy(alpha = 0.14f),
                            radius = radiusPx,
                            center = zoneCenter
                        )
                        drawCircle(
                            color = zoneColor.copy(alpha = 0.72f),
                            radius = radiusPx,
                            center = zoneCenter,
                            style = Stroke(
                                width = 1.8.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                            )
                        )
                        if (radiusPx >= 28.dp.toPx()) {
                            drawCircle(
                                color = zoneColor.copy(alpha = 0.35f),
                                radius = radiusPx * 0.62f,
                                center = zoneCenter,
                                style = Stroke(width = 1.1.dp.toPx())
                            )
                        }
                        if (zoom >= 10.6) {
                            drawContext.canvas.nativeCanvas.drawText(
                                "${zone.name} (${zone.depthRangeLabel})",
                                rx - radiusPx * 0.45f,
                                ry - radiusPx - 6.dp.toPx(),
                                reefZoneLabelPaint
                            )
                        }
                    }
                }

                // Draw popular Pulau Bintan Spot Gacor markers
                BintanReefAtlas.hotspots.forEach { spot ->
                    val (hx, hy) = WebMercator.latLonToScreen(
                        targetLat = spot.latitude,
                        targetLon = spot.longitude,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom,
                        baseTilePx = baseTilePx,
                        viewportWidth = widthD,
                        viewportHeight = heightD
                    )
                    if (hx in -80f..(size.width + 80f) && hy in -80f..(size.height + 80f)) {
                        val isSpotSelected = selectedHotspot?.id == spot.id
                        val spotColor = reefColorFor(spot.bottomType)
                        val spotCenter = Offset(hx, hy)

                        if (isSpotSelected) {
                            drawCircle(
                                color = spotColor.copy(alpha = 0.30f),
                                radius = 24.dp.toPx(),
                                center = spotCenter
                            )
                            drawCircle(
                                color = spotColor,
                                radius = 16.dp.toPx(),
                                center = spotCenter,
                                style = Stroke(width = 2.2.dp.toPx())
                            )
                        } else {
                            drawCircle(
                                color = spotColor.copy(alpha = 0.22f),
                                radius = 13.dp.toPx(),
                                center = spotCenter
                            )
                        }

                        val markerRadius = if (isSpotSelected) 9.5.dp.toPx() else if (spot.accessType == SpotAccessType.SHORE) 8.dp.toPx() else 7.dp.toPx()
                        drawCircle(
                            color = spotColor,
                            radius = markerRadius,
                            center = spotCenter
                        )
                        drawCircle(
                            color = backgroundColor,
                            radius = markerRadius,
                            center = spotCenter,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = if (spot.accessType == SpotAccessType.SHORE) 3.dp.toPx() else 2.4.dp.toPx(),
                            center = spotCenter
                        )

                        if (isSpotSelected || zoom >= 10.4) {
                            val shortTitle = spot.name.substringBefore(" (")
                            val displayTitle = if (isSpotSelected) "★ $shortTitle (${spot.depthRange})" else shortTitle
                            drawContext.canvas.nativeCanvas.drawText(
                                displayTitle,
                                hx + 11.dp.toPx(),
                                hy + 5.dp.toPx(),
                                hotspotLabelPaint
                            )
                        }
                    }
                }
            }

            // 2d. Active Searched Location Crosshair Marker
            searchedPin?.let { pin ->
                val (sx, sy) = WebMercator.latLonToScreen(
                    targetLat = pin.latitude,
                    targetLon = pin.longitude,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    baseTilePx = baseTilePx,
                    viewportWidth = widthD,
                    viewportHeight = heightD
                )
                if (sx in -80f..(size.width + 80f) && sy in -80f..(size.height + 80f)) {
                    val pinOffset = Offset(sx, sy)
                    drawCircle(
                        color = secondaryColor.copy(alpha = 0.24f),
                        radius = 22.dp.toPx(),
                        center = pinOffset
                    )
                    drawCircle(
                        color = secondaryColor,
                        radius = 13.dp.toPx(),
                        center = pinOffset,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = secondaryColor,
                        radius = 4.dp.toPx(),
                        center = pinOffset
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        pin.name,
                        sx + 15.dp.toPx(),
                        sy - 6.dp.toPx(),
                        routeBadgePaint
                    )
                }
            }

            // 3. Vessel Breadcrumb Wake Track
            if (vesselTrack.size >= 2) {
                val trackPath = Path()
                vesselTrack.forEachIndexed { index, pt ->
                    val (px, py) = WebMercator.latLonToScreen(
                        pt.latitude, pt.longitude, centerLat, centerLon, zoom, baseTilePx, widthD, heightD
                    )
                    if (index == 0) trackPath.moveTo(px, py) else trackPath.lineTo(px, py)
                }
                drawPath(
                    path = trackPath,
                    color = secondaryColor.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                )
            }

            val (vesselX, vesselY) = WebMercator.latLonToScreen(
                gpsTelemetry.latitude,
                gpsTelemetry.longitude,
                centerLat,
                centerLon,
                zoom,
                baseTilePx,
                widthD,
                heightD
            )
            val vesselOffset = Offset(vesselX, vesselY)

            // 4. Active Route / Course Line from Live Vessel GPS -> Locked Waypoint
            if (selectedWaypoint != null) {
                val (targetX, targetY) = WebMercator.latLonToScreen(
                    selectedWaypoint.latitude,
                    selectedWaypoint.longitude,
                    centerLat,
                    centerLon,
                    zoom,
                    baseTilePx,
                    widthD,
                    heightD
                )
                val targetOffset = Offset(targetX, targetY)

                // Soft glowing outer course line
                drawLine(
                    color = primaryColor.copy(alpha = 0.25f),
                    start = vesselOffset,
                    end = targetOffset,
                    strokeWidth = 9.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Crisp dashed sonar-green course line
                drawLine(
                    color = primaryColor,
                    start = vesselOffset,
                    end = targetOffset,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f))
                )

                // Mid-route distance & bearing readout
                if (targetDistanceMeters != null && targetBearing != null) {
                    val midX = (vesselX + targetX) / 2f
                    val midY = (vesselY + targetY) / 2f - 14f
                    val distLabel = MarineGeoUtils.formatDistance(targetDistanceMeters, settings.distanceUnit)
                    val routeText = "$distLabel • ${targetBearing.roundToInt()}°"
                    drawContext.canvas.nativeCanvas.drawText(
                        routeText,
                        midX,
                        midY,
                        routeBadgePaint
                    )
                }
            }

            // 5. Saved Waypoint Pins
            waypoints.forEach { wp ->
                val (wx, wy) = WebMercator.latLonToScreen(
                    wp.latitude, wp.longitude, centerLat, centerLon, zoom, baseTilePx, widthD, heightD
                )
                val isSelected = selectedWaypoint?.id == wp.id
                val pinColor = if (isSelected) primaryColor else tertiaryColor
                val pinCenter = Offset(wx, wy)

                if (isSelected) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.24f),
                        radius = 22.dp.toPx(),
                        center = pinCenter
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 15.dp.toPx(),
                        center = pinCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Marine diamond waypoint icon
                val r = if (isSelected) 9.dp.toPx() else 7.dp.toPx()
                val diamond = Path().apply {
                    moveTo(wx, wy - r)
                    lineTo(wx + r, wy)
                    lineTo(wx, wy + r)
                    lineTo(wx - r, wy)
                    close()
                }
                drawPath(path = diamond, color = pinColor)
                drawPath(path = diamond, color = backgroundColor, style = Stroke(width = 1.8.dp.toPx()))

                // Spot name + depth tag
                val depthShort = MarineGeoUtils.formatDepth(wp.depthMeters, settings.depthUnit)
                drawContext.canvas.nativeCanvas.drawText(
                    "${wp.name} ($depthShort)",
                    wx + 12.dp.toPx(),
                    wy + 4.dp.toPx(),
                    labelTextPaint
                )
            }

            // 6. Shore Casting Distance Rings (25m / 50m / 100m at true WebMercator meter scale)
            if (showCastingRings) {
                val mpp = WebMercator.metersPerPixel(gpsTelemetry.latitude, zoom, baseTilePx)
                val ringDistancesMeters = listOf(25.0, 50.0, 100.0)
                ringDistancesMeters.forEach { distM ->
                    val radiusPx = (distM / mpp).toFloat()
                    if (radiusPx in 18f..1600f) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.65f),
                            radius = radiusPx,
                            center = vesselOffset,
                            style = Stroke(
                                width = 1.6.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                            )
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${distM.toInt()}m",
                            vesselOffset.x + 6.dp.toPx(),
                            vesselOffset.y - radiusPx - 4.dp.toPx(),
                            routeBadgePaint
                        )
                    }
                }
            }

            // 7. Live Vessel GPS Marker + Rotating Real-Time Compass Heading Cone
            val accuracyMeters = gpsTelemetry.accuracyMeters
            if (accuracyMeters != null && accuracyMeters > 0f) {
                val mpp = WebMercator.metersPerPixel(gpsTelemetry.latitude, zoom, baseTilePx)
                val accRadiusPx = (accuracyMeters / mpp).toFloat().coerceIn(14.dp.toPx(), 90.dp.toPx())
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.14f),
                    radius = accRadiusPx,
                    center = vesselOffset
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.45f),
                    radius = accRadiusPx,
                    center = vesselOffset,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            rotate(degrees = compassTelemetry.azimuthDegrees, pivot = vesselOffset) {
                // Compass heading cone beam
                val coneLen = 46.dp.toPx()
                val coneHalfWidth = 18.dp.toPx()
                val conePath = Path().apply {
                    moveTo(vesselOffset.x, vesselOffset.y)
                    lineTo(vesselOffset.x - coneHalfWidth, vesselOffset.y - coneLen)
                    lineTo(vesselOffset.x + coneHalfWidth, vesselOffset.y - coneLen)
                    close()
                }
                drawPath(
                    path = conePath,
                    color = primaryColor.copy(alpha = 0.28f)
                )

                // Forward heading vector line
                drawLine(
                    color = primaryColor.copy(alpha = 0.75f),
                    start = vesselOffset,
                    end = Offset(vesselOffset.x, vesselOffset.y - coneLen * 1.15f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Garmin-style vessel chevron icon
                val bow = 14.dp.toPx()
                val wing = 10.dp.toPx()
                val boatPath = Path().apply {
                    moveTo(vesselOffset.x, vesselOffset.y - bow)
                    lineTo(vesselOffset.x + wing, vesselOffset.y + bow * 0.75f)
                    lineTo(vesselOffset.x, vesselOffset.y + bow * 0.35f)
                    lineTo(vesselOffset.x - wing, vesselOffset.y + bow * 0.75f)
                    close()
                }
                drawPath(path = boatPath, color = primaryColor)
                drawPath(
                    path = boatPath,
                    color = backgroundColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // TOP OVERLAY: Layer Switcher Bar + Live Telemetry Strip
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Layer Selector Row (Satelit Karang / Batimetri / Karang & Spot / Daftar Spot / Laut Dark / OSM)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showSearchDialog || searchedPin != null,
                    onClick = { showSearchDialog = true },
                    label = {
                        Text(
                            text = stringResource(R.string.map_search_location),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Cari lokasi atau koordinat",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = surfaceColor.copy(alpha = 0.90f),
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                        selectedLabelColor = secondaryColor,
                        selectedLeadingIconColor = secondaryColor
                    ),
                    modifier = Modifier.testTag("map_search_chip")
                )
                LayerChip(
                    label = stringResource(R.string.map_layer_satellite),
                    selected = activeLayer == ChartBaseLayer.SATELLITE_REEF,
                    onClick = {
                        activeLayer = ChartBaseLayer.SATELLITE_REEF
                        zoom = zoom.coerceAtMost(ChartBaseLayer.SATELLITE_REEF.maxZoom.toDouble())
                    },
                    testTag = "map_layer_satellite_chip"
                )
                FilterChip(
                    selected = showReefHotspots,
                    onClick = { showReefHotspots = !showReefHotspots },
                    label = {
                        Text(
                            text = stringResource(R.string.map_overlay_reefs_spots),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Phishing,
                            contentDescription = "Tampilkan zona karang dan spot gacor Bintan",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = surfaceColor.copy(alpha = 0.90f),
                        selectedContainerColor = Color(0xFFFF6B4A).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFFFB020),
                        selectedLeadingIconColor = Color(0xFFFF6B4A)
                    ),
                    modifier = Modifier.testTag("map_reefs_spots_toggle")
                )
                LayerChip(
                    label = stringResource(R.string.map_layer_bathymetry),
                    selected = activeLayer == ChartBaseLayer.OCEAN_BATHYMETRY,
                    onClick = {
                        activeLayer = ChartBaseLayer.OCEAN_BATHYMETRY
                        zoom = zoom.coerceAtMost(ChartBaseLayer.OCEAN_BATHYMETRY.maxZoom.toDouble())
                    },
                    testTag = "map_layer_bathymetry_chip"
                )
                LayerChip(
                    label = stringResource(R.string.map_layer_dark),
                    selected = activeLayer == ChartBaseLayer.MARINE_DARK,
                    onClick = { activeLayer = ChartBaseLayer.MARINE_DARK },
                    testTag = "map_layer_dark_chip"
                )
                LayerChip(
                    label = stringResource(R.string.map_layer_osm),
                    selected = activeLayer == ChartBaseLayer.OSM_STANDARD,
                    onClick = { activeLayer = ChartBaseLayer.OSM_STANDARD },
                    testTag = "map_layer_osm_chip"
                )
                FilterChip(
                    selected = showCastingRings,
                    onClick = {
                        showCastingRings = !showCastingRings
                        if (showCastingRings && zoom < 14.5) {
                            zoom = 15.2
                            followVessel = true
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.map_casting_rings),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = "Lingkaran jarak lempar 25m 50m 100m",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = surfaceColor.copy(alpha = 0.90f),
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("map_casting_rings_toggle")
                )
                FilterChip(
                    selected = showStormRadar,
                    onClick = {
                        showStormRadar = !showStormRadar
                        if (showStormRadar) {
                            if (radarMeta == null) onRefreshWeather()
                            if (zoom > 11.5) {
                                followVessel = false
                                zoom = 9.8
                            }
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.map_overlay_storm_radar),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Thunderstorm,
                            contentDescription = "Aktifkan radar hujan dan badai",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = surfaceColor.copy(alpha = 0.90f),
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        selectedLabelColor = errorColor,
                        selectedLeadingIconColor = errorColor
                    ),
                    modifier = Modifier.testTag("map_storm_radar_toggle")
                )
                FilterChip(
                    selected = showOpenSeaMap,
                    onClick = { showOpenSeaMap = !showOpenSeaMap },
                    label = {
                        Text(
                            text = "+ ${stringResource(R.string.map_overlay_openseamap)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = surfaceColor.copy(alpha = 0.90f),
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                        selectedLabelColor = MaterialTheme.colorScheme.secondary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("map_openseamap_toggle")
                )
                FilterChip(
                    selected = offlineState.savedTileCount > 0 || offlineState.isDownloading,
                    onClick = { showOfflineDialog = true },
                    label = {
                        Text(
                            text = when {
                                offlineState.isDownloading && offlineState.totalCount > 0 ->
                                    "Unduh ${offlineState.downloadedCount}/${offlineState.totalCount}"
                                offlineState.savedTileCount > 0 ->
                                    "Offline (${offlineState.savedTileCount})"
                                else -> stringResource(R.string.map_save_offline)
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (offlineState.savedTileCount > 0 && !offlineState.isDownloading) {
                                Icons.Rounded.OfflinePin
                            } else {
                                Icons.Rounded.CloudDownload
                            },
                            contentDescription = "Simpan peta offline",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = surfaceColor.copy(alpha = 0.90f),
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        selectedLabelColor = primaryColor,
                        selectedLeadingIconColor = primaryColor
                    ),
                    modifier = Modifier.testTag("map_save_offline_chip")
                )
            }

            // Live Offline Map Download Progress Bar Banner
            if (offlineState.isDownloading) {
                val progress = if (offlineState.totalCount > 0) {
                    (offlineState.downloadedCount.toFloat() / offlineState.totalCount.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor.copy(alpha = 0.94f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, primaryColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = offlineState.statusMessage ?: "Mengunduh peta offline...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = textPrimaryColor
                                )
                            }
                            IconButton(
                                onClick = { tileManager.cancelOfflineDownload() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("cancel_offline_download_banner_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Batalkan unduhan peta offline",
                                    tint = errorColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = primaryColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // Live Storm Radar Legend & Time-Frame Bar
            if (showStormRadar) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor.copy(alpha = 0.93f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, errorColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Thunderstorm,
                                    contentDescription = null,
                                    tint = errorColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (activeRadarFrame != null) {
                                        "RADAR HUJAN • ${activeRadarFrame.timeLabel}"
                                    } else {
                                        "RADAR HUJAN & BADAI BINTAN"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = textPrimaryColor
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (radarMeta != null && radarMeta.frames.size > 1) {
                                    val curIdx = if (selectedRadarFrameIdx in radarMeta.frames.indices) {
                                        selectedRadarFrameIdx
                                    } else {
                                        radarMeta.latestFrameIndex
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedRadarFrameIdx = (curIdx - 1).coerceAtLeast(0)
                                        },
                                        enabled = curIdx > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronLeft,
                                            contentDescription = "Frame radar sebelumnya",
                                            tint = secondaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedRadarFrameIdx = (curIdx + 1).coerceAtMost(radarMeta.frames.lastIndex)
                                        },
                                        enabled = curIdx < radarMeta.frames.lastIndex,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Frame radar berikutnya",
                                            tint = secondaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onRefreshWeather,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Segarkan radar hujan",
                                        tint = primaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadarLegendDot(color = Color(0xFF38BDF8), label = "Gerimis")
                            RadarLegendDot(color = Color(0xFF22C55E), label = "Hujan")
                            RadarLegendDot(color = Color(0xFFFACC15), label = "Lebat")
                            RadarLegendDot(color = Color(0xFFEF4444), label = "Badai Petir")
                        }
                    }
                }
            } else if (activeLayer == ChartBaseLayer.SATELLITE_REEF && showCastingRings) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = surfaceColor.copy(alpha = 0.86f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, gridColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = stringResource(R.string.map_shore_reef_legend),
                        style = MaterialTheme.typography.labelSmall,
                        color = textPrimaryColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Live Mini Heading & GPS Status Pill
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = surfaceColor.copy(alpha = 0.90f),
                    modifier = Modifier.border(
                        1.dp,
                        gridColor,
                        RoundedCornerShape(10.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (gpsTelemetry.isLiveFix) primaryColor else tertiaryColor)
                        )
                        val hdg = compassTelemetry.azimuthDegrees.roundToInt() % 360
                        Text(
                            text = "HDG ${hdg.toString().padStart(3, '0')}° ${MarineGeoUtils.cardinalDirection(compassTelemetry.azimuthDegrees)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = primaryColor
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = gridColor
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f kn", gpsTelemetry.speedKnots),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor
                        )
                    }
                }

                // Live Tile Loading / Offline Retry Status Pill
                val tilePillText = when {
                    loadStatus.activeDownloads > 0 -> "MEMUAT PETA..."
                    loadStatus.loadedCount > 0 -> "PETA AKTIF"
                    loadStatus.failedCount > 0 -> "OFFLINE • MUAT ULANG"
                    else -> "MENYIAPKAN..."
                }
                val isTileError = loadStatus.activeDownloads == 0 && loadStatus.loadedCount == 0 && loadStatus.failedCount > 0
                Surface(
                    onClick = { if (isTileError) tileManager.retryFailedTiles() },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isTileError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f)
                    } else {
                        surfaceColor.copy(alpha = 0.90f)
                    },
                    modifier = Modifier.border(
                        1.dp,
                        if (isTileError) errorColor else gridColor,
                        RoundedCornerShape(10.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isTileError) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Muat ulang peta",
                                tint = errorColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = tilePillText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTileError) errorColor else secondaryColor
                        )
                    }
                }

                if (!gpsTelemetry.hasPermission) {
                    Surface(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                        modifier = Modifier.border(1.dp, tertiaryColor, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GpsFixed,
                                contentDescription = "Aktifkan GPS",
                                tint = tertiaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "GPS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tertiaryColor
                            )
                        }
                    }
                }
            }

            // Drop-Pin Mode Banner
            if (dropPinMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, primaryColor, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.map_drop_pin_hint),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = primaryColor
                        )
                        IconButton(
                            onClick = { dropPinMode = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Batalkan tandai spot",
                                tint = primaryColor
                            )
                        }
                    }
                }
            }

            // Active Searched Location Banner (Save or Dismiss)
            val activeSearchPin = searchedPin
            if (activeSearchPin != null && !dropPinMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, secondaryColor, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pin: ${activeSearchPin.name}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = secondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = MarineGeoUtils.formatDecimalCoords(activeSearchPin.latitude, activeSearchPin.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = textPrimaryColor
                            )
                        }
                        TextButton(
                            onClick = {
                                pendingPinCoords = LatLon(activeSearchPin.latitude, activeSearchPin.longitude)
                            },
                            modifier = Modifier.testTag("searched_pin_save_button")
                        ) {
                            Text(
                                text = "Simpan Spot",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = primaryColor
                            )
                        }
                        IconButton(
                            onClick = { searchedPin = null },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("searched_pin_dismiss_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Tutup pin lokasi pencarian",
                                tint = secondaryColor
                            )
                        }
                    }
                }
            }
        }

        // RIGHT FLOATING CHARTPLOTTER CONTROLS (Zoom +/-, Follow GPS, Drop Pin, Focus Target)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartControlButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Perbesar peta",
                active = false,
                onClick = {
                    zoom = (zoom + 1.0).coerceAtMost(activeLayer.maxZoom.toDouble())
                },
                testTag = "map_zoom_in_button"
            )
            ChartControlButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Perkecil peta",
                active = false,
                onClick = {
                    zoom = (zoom - 1.0).coerceAtLeast(5.0)
                },
                testTag = "map_zoom_out_button"
            )
            ChartControlButton(
                icon = Icons.Rounded.MyLocation,
                contentDescription = stringResource(R.string.map_follow_gps),
                active = followVessel,
                onClick = {
                    followVessel = true
                    searchedPin = null
                    centerLat = gpsTelemetry.latitude
                    centerLon = gpsTelemetry.longitude
                },
                testTag = "map_center_gps_button"
            )
            ChartControlButton(
                icon = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.map_search_location),
                active = showSearchDialog,
                onClick = { showSearchDialog = true },
                testTag = "map_search_button"
            )
            ChartControlButton(
                icon = Icons.Rounded.Phishing,
                contentDescription = stringResource(R.string.map_spot_atlas_button),
                active = showSpotAtlasDialog,
                onClick = { showSpotAtlasDialog = true },
                testTag = "map_spot_atlas_button"
            )
            ChartControlButton(
                icon = Icons.Rounded.AddLocationAlt,
                contentDescription = "Tandai spot baru di peta",
                active = dropPinMode,
                onClick = { dropPinMode = !dropPinMode },
                testTag = "map_drop_pin_button"
            )
            if (selectedWaypoint != null) {
                ChartControlButton(
                    icon = Icons.Rounded.CenterFocusStrong,
                    contentDescription = "Pusatkan ke waypoint target",
                    active = false,
                    onClick = {
                        followVessel = false
                        centerLat = selectedWaypoint.latitude
                        centerLon = selectedWaypoint.longitude
                        if (zoom < 12.5) zoom = 13.0
                    },
                    testTag = "map_focus_target_button"
                )
            }
        }

        // BOTTOM-LEFT NAUTICAL SCALE BAR ABOVE TARGET CARD
        val scaleBarMeters = remember(centerLat, zoom, baseTilePx) {
            val mpp = WebMercator.metersPerPixel(centerLat, zoom, baseTilePx)
            val approx100dpPx = with(density) { 88.dp.toPx().toDouble() }
            val rawMeters = mpp * approx100dpPx
            val niceSteps = listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0)
            niceSteps.minByOrNull { abs(it - rawMeters) } ?: 1000.0
        }
        val scaleBarWidthDp = remember(scaleBarMeters, centerLat, zoom, baseTilePx) {
            val mpp = WebMercator.metersPerPixel(centerLat, zoom, baseTilePx)
            val px = (scaleBarMeters / mpp).toFloat().coerceIn(40f, 320f)
            with(density) { px.toDp() }
        }
        if (selectedHotspot == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 92.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(surfaceColor.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = MarineGeoUtils.formatDistance(scaleBarMeters, settings.distanceUnit),
                    style = MaterialTheme.typography.labelSmall,
                    color = textPrimaryColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(scaleBarWidthDp)
                        .height(3.dp)
                        .background(primaryColor)
                )
            }
        }

        // BOTTOM FLOATING HUD: Curated Spot Gacor Detail Card OR Active Route & Target Card
        val activeHotspot = selectedHotspot
        if (activeHotspot != null) {
            SelectedHotspotDetailCard(
                spot = activeHotspot,
                userLat = gpsTelemetry.latitude,
                userLon = gpsTelemetry.longitude,
                distanceUnit = settings.distanceUnit,
                onSaveAndLockRoute = { spot ->
                    onSaveWaypointAt(
                        spot.name,
                        spot.latitude,
                        spot.longitude,
                        spot.depthMetersEstimate,
                        spot.targetFish,
                        "${spot.bottomType.label} (${spot.depthRange}) • Umpan: ${spot.bestBait} • ${spot.castTip}"
                    )
                    selectedHotspot = null
                },
                onDismiss = { selectedHotspot = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor.copy(alpha = 0.94f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .border(
                        width = 1.dp,
                        color = if (selectedWaypoint != null) primaryColor.copy(alpha = 0.65f) else gridColor,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                if (selectedWaypoint != null && targetDistanceMeters != null && targetBearing != null) {
                    val isOnCourse = signedTurn != null && abs(signedTurn) <= 12f
                    val steerText = when {
                        signedTurn == null -> "RUTE AKTIF"
                        isOnCourse -> "HALUAN TEPAT"
                        signedTurn > 0f -> "KANAN ${signedTurn.roundToInt()}°"
                        else -> "KIRI ${abs(signedTurn).roundToInt()}°"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Sailing,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedWaypoint.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textPrimaryColor
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Kedalaman ${MarineGeoUtils.formatDepth(selectedWaypoint.depthMeters, settings.depthUnit)} • " +
                                    "BRG ${targetBearing.roundToInt()}° ${MarineGeoUtils.cardinalDirection(targetBearing)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryColor
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MarineGeoUtils.formatDistance(targetDistanceMeters, settings.distanceUnit),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = primaryColor
                            )
                            Text(
                                text = steerText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isOnCourse) primaryColor else tertiaryColor
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = null,
                                tint = secondaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = MarineGeoUtils.formatDecimalCoords(centerLat, centerLon),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryColor
                                )
                                Text(
                                    text = "Ketuk titik oranye di peta untuk info karang & spot gacor, atau tekan ikon kail di kanan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Z${String.format(Locale.US, "%.1f", zoom)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor
                        )
                    }
                }
            }
        }
    }

    // Dialog to save waypoint dropped directly on the Chartplotter Map
    val pinCoords = pendingPinCoords
    if (pinCoords != null) {
        DropPinWaypointDialog(
            coords = pinCoords,
            depthUnit = settings.depthUnit,
            onDismiss = { pendingPinCoords = null },
            onConfirm = { name, depthMeters, targetFish, notes ->
                onSaveWaypointAt(
                    name,
                    pinCoords.latitude,
                    pinCoords.longitude,
                    depthMeters,
                    targetFish,
                    notes
                )
                pendingPinCoords = null
            }
        )
    }

    if (showSpotAtlasDialog) {
        BintanSpotAtlasDialog(
            userLat = gpsTelemetry.latitude,
            userLon = gpsTelemetry.longitude,
            distanceUnit = settings.distanceUnit,
            defaultShoreFirst = isShoreMode,
            onSelectSpot = { spot ->
                showSpotAtlasDialog = false
                showReefHotspots = true
                followVessel = false
                selectedHotspot = spot
                centerLat = spot.latitude
                centerLon = spot.longitude
                activeLayer = ChartBaseLayer.SATELLITE_REEF
                zoom = if (spot.accessType == SpotAccessType.SHORE) 15.0 else 13.5
            },
            onDismiss = { showSpotAtlasDialog = false }
        )
    }

    if (showSearchDialog) {
        MapLocationSearchDialog(
            onSelectLocation = { loc ->
                showSearchDialog = false
                followVessel = false
                selectedHotspot = null
                searchedPin = loc
                centerLat = loc.latitude
                centerLon = loc.longitude
                zoom = loc.suggestedZoom.coerceAtMost(activeLayer.maxZoom.toDouble())
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showOfflineDialog) {
        OfflineMapDialog(
            activeLayer = activeLayer,
            centerLat = centerLat,
            centerLon = centerLon,
            offlineState = offlineState,
            onStartDownload = {
                tileManager.downloadOfflineRegion(
                    layer = activeLayer,
                    centerLat = centerLat,
                    centerLon = centerLon
                )
            },
            onCancelDownload = { tileManager.cancelOfflineDownload() },
            onClearOffline = { tileManager.clearOfflineTiles() },
            onDismiss = { showOfflineDialog = false }
        )
    }
}

@Composable
private fun LayerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun ChartControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val borderColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val tintColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier
            .size(48.dp)
            .border(1.2.dp, borderColor, RoundedCornerShape(12.dp))
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tintColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DropPinWaypointDialog(
    coords: LatLon,
    depthUnit: DepthUnit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, depthMeters: Double, targetFish: String, notes: String) -> Unit
) {
    var spotName by remember { mutableStateOf("") }
    var depthInput by remember { mutableStateOf("16") }
    var targetFish by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Tandai Spot dari Peta",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Koordinat: ${MarineGeoUtils.formatDecimalCoords(coords.latitude, coords.longitude)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = spotName,
                    onValueChange = {
                        spotName = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.label_spot_name)) },
                    isError = showError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = depthInput,
                    onValueChange = { depthInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("${stringResource(R.string.label_spot_depth)} (${depthUnit.shortUnit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetFish,
                    onValueChange = { targetFish = it },
                    label = { Text(stringResource(R.string.label_spot_fish)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_spot_notes)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (spotName.isBlank()) {
                        showError = true
                    } else {
                        val parsed = depthInput.toDoubleOrNull() ?: 0.0
                        val meters = when (depthUnit) {
                            DepthUnit.METERS -> parsed
                            DepthUnit.FEET -> parsed / 3.28084
                        }
                        onConfirm(spotName, meters, targetFish, notes)
                    }
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun RadarLegendDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfflineMapDialog(
    activeLayer: ChartBaseLayer,
    centerLat: Double,
    centerLon: Double,
    offlineState: OfflineMapState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onClearOffline: () -> Unit,
    onDismiss: () -> Unit
) {
    val layerTitle = when (activeLayer) {
        ChartBaseLayer.MARINE_DARK -> stringResource(R.string.map_layer_dark)
        ChartBaseLayer.SATELLITE_REEF -> stringResource(R.string.map_layer_satellite)
        ChartBaseLayer.OCEAN_BATHYMETRY -> stringResource(R.string.map_layer_bathymetry)
        ChartBaseLayer.OSM_STANDARD -> stringResource(R.string.map_layer_osm)
    }
    val progress = if (offlineState.totalCount > 0) {
        (offlineState.downloadedCount.toFloat() / offlineState.totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = if (offlineState.savedTileCount > 0) {
                    Icons.Rounded.OfflinePin
                } else {
                    Icons.Rounded.CloudDownload
                },
                contentDescription = "Ikon peta offline",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.map_offline_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Unduh area peta di sekitar titik tengah saat ini agar karang, garis pantai, dan spot tetap tampil jernih meski sinyal hilang di laut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Layer aktif: $layerTitle",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pusat: ${MarineGeoUtils.formatDecimalCoords(centerLat, centerLon)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Cakupan: Radius ±4,5 km • Zoom 11–16 (detail pantai & karang)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tersimpan: ${offlineState.savedTileCount} ubin (${String.format(Locale.US, "%.1f", offlineState.savedSizeMb)} MB)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (offlineState.isDownloading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }

                if (!offlineState.statusMessage.isNullOrBlank()) {
                    Text(
                        text = offlineState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (offlineState.savedTileCount > 0 && !offlineState.isDownloading) {
                    TextButton(
                        onClick = onClearOffline,
                        modifier = Modifier.testTag("clear_offline_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hapus Peta Offline Tersimpan",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (offlineState.isDownloading) {
                Button(
                    onClick = onCancelDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("cancel_offline_dialog_button")
                ) {
                    Text("BATALKAN")
                }
            } else {
                Button(
                    onClick = onStartDownload,
                    modifier = Modifier.testTag("confirm_save_offline_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SIMPAN PETA OFFLINE")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}


