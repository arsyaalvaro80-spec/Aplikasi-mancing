package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.map.BintanHotspot
import com.example.data.map.BintanReefAtlas
import com.example.data.map.ReefBottomType
import com.example.data.map.SpotAccessType
import com.example.data.preferences.DistanceUnit
import com.example.sensors.MarineGeoUtils
import kotlin.math.roundToInt

fun reefColorFor(bottomType: ReefBottomType): Color = when (bottomType) {
    ReefBottomType.CORAL_REEF -> Color(0xFFFF6B4A)      // Bright coral reef orange-red
    ReefBottomType.ROCKY_HEADLAND -> Color(0xFFFFB020)  // Warm granite amber-gold
    ReefBottomType.DROP_OFF -> Color(0xFF00D2FF)        // Deep tubir electric cyan
    ReefBottomType.SEAGRASS_SAND -> Color(0xFF34D399)   // Coastal seagrass emerald
}

@Composable
fun SelectedHotspotDetailCard(
    spot: BintanHotspot,
    userLat: Double,
    userLon: Double,
    distanceUnit: DistanceUnit,
    onSaveAndLockRoute: (BintanHotspot) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = reefColorFor(spot.bottomType)
    val distMeters = remember(spot, userLat, userLon) {
        MarineGeoUtils.distanceMeters(userLat, userLon, spot.latitude, spot.longitude)
    }
    val bearingDeg = remember(spot, userLat, userLon) {
        MarineGeoUtils.bearingDegrees(userLat, userLon, spot.latitude, spot.longitude)
    }
    val distLabel = MarineGeoUtils.formatDistance(distMeters, distanceUnit)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (spot.accessType == SpotAccessType.SHORE) "SPOT PINGGIR" else "KARANG PERAHU",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "${spot.bottomType.shortTag} • ${spot.depthRange}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = spot.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${spot.area} • $distLabel (${bearingDeg.roundToInt()}° ${MarineGeoUtils.cardinalDirection(bearingDeg)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("close_hotspot_detail_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup info spot",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HotspotInfoLine(label = "Target Ikan", value = spot.targetFish, valueColor = MaterialTheme.colorScheme.primary)
                HotspotInfoLine(label = "Umpan", value = spot.bestBait)
                HotspotInfoLine(label = "Waktu/Pasang", value = spot.bestTide)
                HotspotInfoLine(label = "Tips Karang", value = spot.castTip)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSaveAndLockRoute(spot) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_hotspot_as_waypoint_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SIMPAN & KUNCI RUTE",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun HotspotInfoLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            modifier = Modifier.weight(1f)
        )
    }
}

private enum class SpotFilterTab(val label: String) {
    ALL("Semua (24)"),
    SHORE("Pinggir"),
    BOAT("Perahu"),
    KEPRI_OTHER("Luar Bintan")
}

@Composable
fun BintanSpotAtlasDialog(
    userLat: Double,
    userLon: Double,
    distanceUnit: DistanceUnit,
    defaultShoreFirst: Boolean,
    onSelectSpot: (BintanHotspot) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf(if (defaultShoreFirst) SpotFilterTab.SHORE else SpotFilterTab.ALL)
    }

    val sortedSpots = remember(userLat, userLon, selectedFilter) {
        BintanReefAtlas.sortedByDistance(userLat, userLon).filter { (spot, _) ->
            when (selectedFilter) {
                SpotFilterTab.ALL -> true
                SpotFilterTab.SHORE -> spot.accessType == SpotAccessType.SHORE
                SpotFilterTab.BOAT -> spot.accessType == SpotAccessType.BOAT
                SpotFilterTab.KEPRI_OTHER -> !spot.isBintanCore
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Phishing,
                    contentDescription = "Daftar Spot Gacor Bintan dan Kepri",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Spot Gacor & Karang Kepri",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Bintan, Batam, Karimun, Lingga, Anambas & Natuna",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpotFilterTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("spot_filter_${tab.name.lowercase()}")
                        )
                    }
                }

                // Reef color legend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReefMiniLegendDot(color = reefColorFor(ReefBottomType.CORAL_REEF), label = "Karang")
                    ReefMiniLegendDot(color = reefColorFor(ReefBottomType.ROCKY_HEADLAND), label = "Batu")
                    ReefMiniLegendDot(color = reefColorFor(ReefBottomType.DROP_OFF), label = "Palung")
                    ReefMiniLegendDot(color = reefColorFor(ReefBottomType.SEAGRASS_SAND), label = "Lamun")
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(sortedSpots, key = { it.first.id }) { (spot, distMeters) ->
                        val accent = reefColorFor(spot.bottomType)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .clickable { onSelectSpot(spot) }
                                .testTag("atlas_spot_${spot.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
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
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = spot.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = MarineGeoUtils.formatDistance(distMeters, distanceUnit),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (spot.accessType == SpotAccessType.SHORE) {
                                            Icons.Rounded.LocationOn
                                        } else {
                                            Icons.Rounded.DirectionsBoat
                                        },
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${spot.accessType.shortLabel} • ${spot.bottomType.shortTag} (${spot.depthRange})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Text(
                                    text = "Ikan: ${spot.targetFish}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = spot.popularityNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun ReefMiniLegendDot(color: Color, label: String) {
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
