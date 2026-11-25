package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.weather.DailyWeatherItem
import com.example.data.weather.HourlyWeatherItem
import com.example.data.weather.RegionalStormZone
import com.example.data.weather.WeatherDashboardData
import com.example.data.weather.WeatherSeverity
import com.example.ui.components.MarineScreenHeader
import com.example.viewmodel.WeatherUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(
    weatherState: WeatherUiState,
    onRefreshWeather: () -> Unit,
    onOpenStormMap: (lat: Double?, lon: Double?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headerStatus = when (weatherState) {
        is WeatherUiState.Ready -> weatherState.data.current.severity.badgeLabel
        is WeatherUiState.Loading -> "MEMUAT..."
        is WeatherUiState.Error -> "OFFLINE"
    }
    val headerActive = weatherState is WeatherUiState.Ready &&
        weatherState.data.current.severity.rank <= WeatherSeverity.CLOUDY.rank

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MarineScreenHeader(
                title = stringResource(R.string.weather_title),
                subtitle = stringResource(R.string.weather_subtitle),
                statusText = headerStatus,
                statusActive = headerActive,
                onOpenSettings = onOpenSettings
            )
        }

        when (weatherState) {
            is WeatherUiState.Loading -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
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
                                    text = "Mengambil Data Cuaca & Radar Badai",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Memindai prakiraan 7 hari & 7 zona pesisir Bintan...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            is WeatherUiState.Error -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Gagal Memuat Data Cuaca",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = weatherState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRefreshWeather,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("retry_weather_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MUAT ULANG CUACA")
                            }
                        }
                    }
                }
            }

            is WeatherUiState.Ready -> {
                val data = weatherState.data

                // 1. Hero Current Weather + Safety Alert + Direct Storm Radar Map CTA
                item {
                    CurrentWeatherHeroCard(
                        data = data,
                        onRefresh = onRefreshWeather,
                        onOpenStormMap = { onOpenStormMap(null, null) }
                    )
                }

                // 2. Live 7-Zone Bintan Regional Storm & Rain Board
                if (data.regionalZones.isNotEmpty()) {
                    item {
                        RegionalStormZonesCard(
                            zones = data.regionalZones,
                            onZoneClick = { zone -> onOpenStormMap(zone.latitude, zone.longitude) }
                        )
                    }
                }

                // 3. Next 24 Hours Hourly Forecast
                if (data.hourlyNext24h.isNotEmpty()) {
                    item {
                        HourlyWeatherStripCard(hourly = data.hourlyNext24h)
                    }
                }

                // 4. Next 7 Days Daily Forecast
                if (data.dailyNext7Days.isNotEmpty()) {
                    item {
                        DailySevenDayCard(daily = data.dailyNext7Days)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentWeatherHeroCard(
    data: WeatherDashboardData,
    onRefresh: () -> Unit,
    onOpenStormMap: () -> Unit
) {
    val current = data.current
    val accent = severityAccentColor(current.severity)
    val bannerBg = severityContainerColor(current.severity)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bannerBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = severityIcon(current.severity),
                            contentDescription = current.conditionLabel,
                            tint = accent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${current.temperatureC.roundToInt()}°C",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = current.conditionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = accent,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = "Terasa ${current.apparentTempC.roundToInt()}°C • Update ${data.updatedAtLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_weather_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Perbarui data cuaca",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Safety Headline Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bannerBg)
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (current.severity.rank >= WeatherSeverity.RAIN.rank) {
                            Icons.Rounded.Warning
                        } else {
                            Icons.Rounded.WbSunny
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = data.safetyHeadline,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                }
                Text(
                    text = data.safetyAdvice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 3 Live Telemetry Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherStatPill(
                    icon = Icons.Rounded.Air,
                    label = "ANGIN / GUST",
                    value = "${String.format(Locale.US, "%.0f", current.windSpeedKnots)}-${String.format(Locale.US, "%.0f", current.windGustsKnots)} kn",
                    sub = "Dari ${current.windDirectionCardinal}",
                    modifier = Modifier.weight(1f)
                )
                WeatherStatPill(
                    icon = Icons.Rounded.WaterDrop,
                    label = "HUJAN",
                    value = "${String.format(Locale.US, "%.1f", current.precipitationMm)} mm",
                    sub = "Awan ${current.cloudCoverPercent}%",
                    modifier = Modifier.weight(1f)
                )
                WeatherStatPill(
                    icon = Icons.Rounded.Cloud,
                    label = "LEMBAP",
                    value = "${current.humidityPercent}%",
                    sub = if (data.radarMetadata != null) "Radar Live" else "Open-Meteo",
                    modifier = Modifier.weight(1f)
                )
            }

            // Primary CTA: Open Interactive Storm & Rain Radar Map
            Button(
                onClick = onOpenStormMap,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("open_storm_radar_map_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_open_storm_radar_map),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun WeatherStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1
        )
    }
}

@Composable
private fun RegionalStormZonesCard(
    zones: List<RegionalStormZone>,
    onZoneClick: (RegionalStormZone) -> Unit
) {
    val activeRainCount = zones.count { it.severity.rank >= WeatherSeverity.RAIN.rank }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Thunderstorm,
                        contentDescription = null,
                        tint = if (activeRainCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.weather_regional_storms_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (activeRainCount > 0) "$activeRainCount ZONA HUJAN" else "SEMUA AMAN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (activeRainCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Ketuk wilayah untuk melihat lokasi awan hujan & badainya langsung di Peta Radar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            zones.forEach { zone ->
                val accent = severityAccentColor(zone.severity)
                val bg = severityContainerColor(zone.severity)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            width = if (zone.severity.rank >= WeatherSeverity.RAIN.rank) 1.dp else 0.75.dp,
                            color = if (zone.severity.rank >= WeatherSeverity.RAIN.rank) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onZoneClick(zone) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = zone.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${zone.conditionLabel} • Hujan ${String.format(Locale.US, "%.1f", zone.precipitationMm)} mm • Gust ${String.format(Locale.US, "%.0f", zone.windGustsKnots)} kn",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when (zone.severity) {
                                WeatherSeverity.STORM -> "BADAI"
                                WeatherSeverity.HEAVY_RAIN -> "LEBAT"
                                WeatherSeverity.RAIN -> "HUJAN"
                                WeatherSeverity.CLOUDY -> "BERAWAN"
                                WeatherSeverity.CLEAR -> "CERAH"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "3j: ${zone.rainProbabilityNext3hPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyWeatherStripCard(hourly: List<HourlyWeatherItem>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Umbrella,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.weather_hourly_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hourly) { item ->
                    val accent = severityAccentColor(item.severity)
                    val isAlert = item.severity.rank >= WeatherSeverity.RAIN.rank
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .width(74.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isAlert) severityContainerColor(item.severity)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isAlert) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = item.timeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = severityIcon(item.severity),
                            contentDescription = item.conditionLabel,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "${item.temperatureC.roundToInt()}°",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${item.rainProbabilityPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.rainProbabilityPercent >= 45) accent else MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.0f", item.windSpeedKnots)} kn",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySevenDayCard(daily: List<DailyWeatherItem>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.weather_daily_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            daily.forEach { day ->
                val accent = severityAccentColor(day.severity)
                val bg = severityContainerColor(day.severity)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = severityIcon(day.severity),
                            contentDescription = day.conditionLabel,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = day.dayLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${day.conditionLabel} • Hujan ${day.rainProbabilityMaxPercent}% (${String.format(Locale.US, "%.1f", day.precipitationSumMm)} mm)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${day.tempMinC.roundToInt()}° – ${day.tempMaxC.roundToInt()}°",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Gust ${String.format(Locale.US, "%.0f", day.maxWindGustsKnots)} kn",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun severityAccentColor(severity: WeatherSeverity): Color = when (severity) {
    WeatherSeverity.CLEAR -> MaterialTheme.colorScheme.primary
    WeatherSeverity.CLOUDY -> MaterialTheme.colorScheme.secondary
    WeatherSeverity.RAIN -> MaterialTheme.colorScheme.tertiary
    WeatherSeverity.HEAVY_RAIN -> Color(0xFFFF8A3D)
    WeatherSeverity.STORM -> MaterialTheme.colorScheme.error
}

@Composable
private fun severityContainerColor(severity: WeatherSeverity): Color = when (severity) {
    WeatherSeverity.CLEAR -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    WeatherSeverity.CLOUDY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    WeatherSeverity.RAIN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
    WeatherSeverity.HEAVY_RAIN -> Color(0xFFFF8A3D).copy(alpha = 0.22f)
    WeatherSeverity.STORM -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
}

private fun severityIcon(severity: WeatherSeverity): ImageVector = when (severity) {
    WeatherSeverity.CLEAR -> Icons.Rounded.WbSunny
    WeatherSeverity.CLOUDY -> Icons.Rounded.Cloud
    WeatherSeverity.RAIN -> Icons.Rounded.Grain
    WeatherSeverity.HEAVY_RAIN -> Icons.Rounded.WaterDrop
    WeatherSeverity.STORM -> Icons.Rounded.Thunderstorm
}
