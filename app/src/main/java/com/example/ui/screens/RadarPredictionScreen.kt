package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.MarinePrediction
import com.example.data.marine.TideTrend
import com.example.data.preferences.FishingMode
import com.example.data.preferences.SonarSettings
import com.example.sensors.CompassTelemetry
import com.example.sensors.GpsTelemetry
import com.example.sensors.MarineGeoUtils
import com.example.ui.components.AiSampleNoticeBanner
import com.example.ui.components.FeedingScoreGauge
import com.example.ui.components.MarineScreenHeader
import com.example.ui.components.SonarRadarCanvas
import com.example.ui.components.TelemetryTile
import com.example.ui.components.TideWaveChartCard
import com.example.viewmodel.MarineScanUiState
import com.example.viewmodel.TideUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RadarPredictionScreen(
    gpsTelemetry: GpsTelemetry,
    compassTelemetry: CompassTelemetry,
    settings: SonarSettings,
    scanState: MarineScanUiState,
    tideState: TideUiState,
    onScanMarine: () -> Unit,
    onRefreshTide: () -> Unit,
    onSetFishingMode: (FishingMode) -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onLocationPermissionResult(granted)
    }

    val listState = rememberLazyListState()
    val isScanning = scanState is MarineScanUiState.Loading
    val currentScore = (scanState as? MarineScanUiState.Success)?.prediction?.feedingScore
    val currentTideTrend = (tideState as? TideUiState.Ready)?.data?.currentTrend

    LaunchedEffect(scanState) {
        if (scanState is MarineScanUiState.Success) {
            listState.animateScrollToItem(index = 6)
        }
    }

    val gpsBadgeText = when {
        gpsTelemetry.isLiveFix -> stringResource(R.string.gps_status_live)
        gpsTelemetry.hasPermission -> stringResource(R.string.gps_status_waiting)
        else -> stringResource(R.string.gps_status_bintan_ref)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MarineScreenHeader(
                title = stringResource(R.string.radar_title),
                subtitle = stringResource(R.string.radar_subtitle),
                statusText = gpsBadgeText,
                statusActive = gpsTelemetry.isLiveFix
            )
        }

        item {
            SonarRadarCanvas(
                headingDegrees = compassTelemetry.azimuthDegrees,
                rangeMeters = settings.sonarRangeMeters,
                isScanning = isScanning,
                feedingScore = currentScore
            )
        }

        // Live GPS Coordinate Strip
        item {
            GpsTelemetryCard(
                gpsTelemetry = gpsTelemetry,
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }

        // 24-Hour Live Rising / Receding Tide Wave Curve Card
        item {
            TideWaveChartCard(
                tideState = tideState,
                onRefreshTide = onRefreshTide
            )
        }

        // Fishing Mode Selector (Mancing Pinggir vs Perahu / Laut)
        item {
            FishingModeSelectorCard(
                selectedMode = settings.fishingMode,
                onSelectMode = onSetFishingMode
            )
        }

        // Primary Action Button: PINDAI KONDISI LAUT
        item {
            Button(
                onClick = onScanMarine,
                enabled = !isScanning,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    disabledContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("scan_marine_button")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.btn_scanning_marine),
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.btn_scan_marine),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Prediction Output Section
        item {
            when (scanState) {
                is MarineScanUiState.Idle -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "SIAP MEMINDAI KONDISI PERAIRAN",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tekan \"PINDAI KONDISI LAUT\" untuk menganalisis koordinat GPS saat ini dengan Gemini AI (cuaca, angin, fase bulan, pasang surut, skor peluang ikan makan, jam gacor & rekomendasi umpan spesifik).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        BintanQuickBaitGuideCard(
                            tideTrend = currentTideTrend,
                            fishingMode = settings.fishingMode
                        )
                    }
                }

                is MarineScanUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Mengkalkulasi Solunar & Kondisi Laut",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = MarineGeoUtils.formatDecimalCoords(
                                        gpsTelemetry.latitude,
                                        gpsTelemetry.longitude
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                is MarineScanUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Gagal Memindai Kondisi Laut",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = scanState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                is MarineScanUiState.Success -> {
                    AnimatedVisibility(visible = true) {
                        MarinePredictionContent(prediction = scanState.prediction)
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsTelemetryCard(
    gpsTelemetry: GpsTelemetry,
    onRequestLocationPermission: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (gpsTelemetry.isLiveFix) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                        contentDescription = "Status GPS",
                        tint = if (gpsTelemetry.isLiveFix) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (gpsTelemetry.isLiveFix) "KOORDINAT GPS LIVE" else "KOORDINAT PERAIRAN BINTAN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f kn", gpsTelemetry.speedKnots),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = MarineGeoUtils.formatDms(gpsTelemetry.latitude, isLatitude = true),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = MarineGeoUtils.formatDms(gpsTelemetry.longitude, isLatitude = false),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MarineGeoUtils.formatDecimalCoords(
                            gpsTelemetry.latitude,
                            gpsTelemetry.longitude
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val accText = gpsTelemetry.accuracyMeters?.let { "Akurasi ±${it.roundToInt()} m" }
                        ?: gpsTelemetry.providerStatus
                    Text(
                        text = accText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!gpsTelemetry.hasPermission) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRequestLocationPermission,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("enable_gps_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GpsFixed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_enable_gps),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun MarinePredictionContent(prediction: MarinePrediction) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (prediction.isSampleMode) {
            AiSampleNoticeBanner(notice = prediction.notice)
        }

        // Hero Feeding Score Gauge Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    RoundedCornerShape(18.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeedingScoreGauge(
                    score = prediction.feedingScore,
                    rating = prediction.feedingRating
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feeding_score_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = prediction.tacticalAdvice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Bait Recommendations Card (Prominent right below Hero Feeding Score)
        BaitRecommendationsCard(
            title = stringResource(R.string.bait_recommendation_label),
            badgeText = stringResource(R.string.badge_gemini_ai),
            baits = prediction.baitRecommendations,
            subtitle = null,
            modifier = Modifier.testTag("ai_bait_recommendations_card")
        )

        // 2x2 Marine Conditions Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryTile(
                icon = Icons.Rounded.WbCloudy,
                label = "CUACA LAUT",
                value = prediction.weatherSummary,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            TelemetryTile(
                icon = Icons.Rounded.Air,
                label = "ANGIN & OMBAK",
                value = prediction.windAndWave,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryTile(
                icon = Icons.Rounded.NightsStay,
                label = "FASE BULAN",
                value = prediction.moonPhase,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            TelemetryTile(
                icon = Icons.Rounded.Water,
                label = "PASANG SURUT",
                value = prediction.tideCondition,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Jam Gacor Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.prime_hours_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                prediction.primeHours.forEach { slot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = slot,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FishingModeSelectorCard(
    selectedMode: FishingMode,
    onSelectMode: (FishingMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.fishing_mode_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = selectedMode.shortLabel.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FishingMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMode(mode) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == FishingMode.SHORE) {
                                    Icons.Rounded.BeachAccess
                                } else {
                                    Icons.Rounded.DirectionsBoat
                                },
                                contentDescription = mode.shortLabel,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = mode.shortLabel,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag(
                                if (mode == FishingMode.SHORE) "mode_shore_chip" else "mode_boat_chip"
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (selectedMode == FishingMode.SHORE) {
                    stringResource(R.string.fishing_mode_shore_desc)
                } else {
                    stringResource(R.string.fishing_mode_boat_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BintanQuickBaitGuideCard(
    tideTrend: TideTrend?,
    fishingMode: FishingMode
) {
    val badgeLabel = tideTrend?.label ?: "PANDUAN BINTAN"
    val guideItems = if (fishingMode == FishingMode.SHORE) {
        when (tideTrend) {
            TideTrend.RISING -> listOf(
                "Umpan Pinggir Pasang Naik: Udang hidup/kupas atau cacing laut (pumpun) — ikan mulai masuk ke tepi karang, dermaga & pantai untuk mencari makan (Kakap Putih, Kerapu Pinggir, Gerot-gerot).",
                "Rangkaian Dasar Ringan: Timah J1–J3 (10–30 gr) dengan kail no. 6–10 dan leader 12–20 lb. Lempar 15–35 meter ke batas air dangkal & dalam.",
                "Ultralight / Light Casting: Minnow 5–9 cm atau micro jig 5–15 gr di sekitar tiang dermaga, muara bakau, atau karang tepi."
            )
            TideTrend.FALLING -> listOf(
                "Umpan Pinggir Surut Turun: Potongan cumi/sotong segar atau ikan tamban iris (aroma kuat tahan arus keluar) untuk Sembilang, Pari, Kerapu Karang & Gerot-gerot.",
                "Posisi Lempar: Bidik palung (warna air lebih gelap) atau saluran keluar air di pinggir pantai/karang tempat ikan menunggu mangsa terbawa arus surut.",
                "Rangkaian: Gunakan timah J3–J4 (30–50 gr) agar umpan tidak mudah terseret arus, atau sistem pelampung gantung di dermaga."
            )
            TideTrend.HIGH_SLACK, TideTrend.LOW_SLACK -> listOf(
                "Umpan Arus Tenang: Lumut hijau / roti tawar kepal (teknik pelampung kail garong/no. 6–8) sangat ampuh untuk Baronang & Kitang di pinggir karang Bintan.",
                "Dasaran Halus: Udang kupas kecil atau kerang/siput pantai untuk Kerong-kerong, Kapas-kapas & ikan karang pinggir.",
                "Tips Joran: Pakai timah paling ringan (J1 / timah merica) agar sensasi sambaran halus ikan pinggiran lebih terasa."
            )
            null -> listOf(
                "Umpan Andalan Pinggir Bintan: Udang kupas/hidup, cacing laut (pumpun), potongan cumi segar, dan lumut/roti (untuk Baronang).",
                "Rangkaian Praktis: Dasaran ringan timah J1–J4 + kail no. 6–10, atau pelampung di dermaga/batu karang tepi agar tidak sering nyangkut.",
                "Casting Ringan: Micro jig 5–15 gr / soft lure udang untuk Kakap Putih, Kerapu Pinggir & Kuwe di pinggir pantai."
            )
        }
    } else {
        when (tideTrend) {
            TideTrend.RISING -> listOf(
                "Umpan Hidup: Udang putih / anak selar hidup (kail 2/0–4/0, teknik dasaran pinggir karang untuk Kerapu & Kakap).",
                "Lure & Jig: Metal jig 60–100 gr warna silver-biru atau pink glow (slow jigging mengikuti arus naik).",
                "Casting & Trolling: Minnow 9–12 cm atau spoon kilap di batas arus karang untuk Tenggiri & GT."
            )
            TideTrend.FALLING -> listOf(
                "Umpan Alami: Potongan sotong segar / ikan tamban iris (dasaran dengan timah J5–J8 untuk Jenaha & Kurisi).",
                "Lure & Jig: Slow pitch jig 80–120 gr warna zebra glow di lereng drop-off saat arus surut menarik ikan ke dalam.",
                "Rangkaian: Gunakan leader fluorocarbon 30–50 lb sepanjang 1.5–2 meter agar tahan gesekan karang Bintan."
            )
            TideTrend.HIGH_SLACK, TideTrend.LOW_SLACK -> listOf(
                "Umpan Alami: Udang kupas / potongan cumi beraroma kuat (arus tenang sangat cocok untuk dasaran ringan).",
                "Lure & Jig: Micro jig 20–40 gr atau soft lure paddle tail (gerakan pelan di dekat struktur karang).",
                "Tips Arus Mati: Turunkan ukuran timah dan kail (no. 1/0–2/0) untuk memancing sambaran ikan karang."
            )
            null -> listOf(
                "Umpan Alami: Udang hidup, sotong segar, atau potongan selar (andalan dasaran Kerapu, Kakap Merah & Jenaha).",
                "Lure & Jig: Metal jig 40–100 gr (silver/pink glow) untuk teknik slow jigging di terumbu karang Bintan.",
                "Casting: Minnow 10 cm atau popper ringan di tepi karang dangkal untuk Tenggiri & GT."
            )
        }
    }

    val cardTitle = if (fishingMode == FishingMode.SHORE) {
        stringResource(R.string.bait_quick_guide_shore_label)
    } else {
        stringResource(R.string.bait_quick_guide_label)
    }

    BaitRecommendationsCard(
        title = cardTitle,
        badgeText = badgeLabel,
        baits = guideItems,
        subtitle = stringResource(R.string.bait_quick_guide_hint),
        modifier = Modifier.testTag("quick_bait_guide_card")
    )
}

@Composable
private fun BaitRecommendationsCard(
    title: String,
    badgeText: String,
    baits: List<String>,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Phishing,
                        contentDescription = "Ikon rekomendasi umpan",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (baits.isEmpty()) {
                Text(
                    text = "Belum ada detail umpan dari pemindaian ini. Tekan \"PINDAI KONDISI LAUT\" kembali untuk memuat rekomendasi umpan terbaru.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                baits.forEachIndexed { index, bait ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            text = bait,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
