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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.WaypointEntity
import com.example.data.preferences.DepthUnit
import com.example.data.preferences.SonarSettings
import com.example.sensors.CompassTelemetry
import com.example.sensors.GpsTelemetry
import com.example.sensors.MarineGeoUtils
import com.example.ui.components.MarineScreenHeader
import com.example.ui.components.WaypointCompassCanvas
import kotlin.math.roundToInt

@Composable
fun WaypointScreen(
    gpsTelemetry: GpsTelemetry,
    compassTelemetry: CompassTelemetry,
    settings: SonarSettings,
    waypoints: List<WaypointEntity>,
    selectedWaypoint: WaypointEntity?,
    onSaveWaypoint: (name: String, depthMeters: Double, targetFish: String, notes: String) -> Unit,
    onSelectWaypoint: (Long) -> Unit,
    onDeleteWaypoint: (Long) -> Unit,
    onOpenChartplotter: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val targetBearing = selectedWaypoint?.let { target ->
        MarineGeoUtils.bearingDegrees(
            fromLat = gpsTelemetry.latitude,
            fromLon = gpsTelemetry.longitude,
            toLat = target.latitude,
            toLon = target.longitude
        )
    }
    val targetDistanceMeters = selectedWaypoint?.let { target ->
        MarineGeoUtils.distanceMeters(
            fromLat = gpsTelemetry.latitude,
            fromLon = gpsTelemetry.longitude,
            toLat = target.latitude,
            toLon = target.longitude
        )
    }
    val targetDistanceLabel = targetDistanceMeters?.let {
        MarineGeoUtils.formatDistance(it, settings.distanceUnit)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MarineScreenHeader(
                title = stringResource(R.string.waypoints_title),
                subtitle = stringResource(R.string.waypoints_subtitle),
                statusText = if (compassTelemetry.hasSensor) "KOMPAS AKTIF" else "NO SENSOR",
                statusActive = compassTelemetry.hasSensor,
                onOpenSettings = onOpenSettings
            )
        }

        // Real-time Compass HUD toward Selected Waypoint
        item {
            WaypointCompassCanvas(
                deviceAzimuthDegrees = compassTelemetry.azimuthDegrees,
                targetBearingDegrees = targetBearing,
                distanceLabel = targetDistanceLabel,
                targetName = selectedWaypoint?.name
            )
        }

        // Active Locked Target Info Strip
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (selectedWaypoint != null) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                if (selectedWaypoint != null) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.compass_locked_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = selectedWaypoint.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = MarineGeoUtils.formatDecimalCoords(
                                        selectedWaypoint.latitude,
                                        selectedWaypoint.longitude
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = MarineGeoUtils.formatDepth(
                                        selectedWaypoint.depthMeters,
                                        settings.depthUnit
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (selectedWaypoint.targetFish.isNotBlank()) {
                                    Text(
                                        text = selectedWaypoint.targetFish,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = onOpenChartplotter,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("open_waypoint_on_map_button")
                        ) {
                            Text(
                                text = stringResource(R.string.map_view_on_chart),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.compass_no_target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Save Current Spot Button
        item {
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_waypoint_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddLocationAlt,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.btn_save_current_spot),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Saved Waypoints Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAFTAR SPOT TERSIMPAN (${waypoints.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "ROOM DB LOKAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (waypoints.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.empty_waypoints_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.empty_waypoints_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(items = waypoints, key = { it.id }) { waypoint ->
                val isSelected = selectedWaypoint?.id == waypoint.id
                val distMeters = MarineGeoUtils.distanceMeters(
                    gpsTelemetry.latitude,
                    gpsTelemetry.longitude,
                    waypoint.latitude,
                    waypoint.longitude
                )
                val brg = MarineGeoUtils.bearingDegrees(
                    gpsTelemetry.latitude,
                    gpsTelemetry.longitude,
                    waypoint.latitude,
                    waypoint.longitude
                )
                WaypointCard(
                    waypoint = waypoint,
                    isSelected = isSelected,
                    distanceText = MarineGeoUtils.formatDistance(distMeters, settings.distanceUnit),
                    bearingText = "${brg.roundToInt()}° ${MarineGeoUtils.cardinalDirection(brg)}",
                    depthText = MarineGeoUtils.formatDepth(waypoint.depthMeters, settings.depthUnit),
                    onSelect = { onSelectWaypoint(waypoint.id) },
                    onDelete = { onDeleteWaypoint(waypoint.id) }
                )
            }
        }
    }

    if (showAddDialog) {
        SaveWaypointDialog(
            gpsTelemetry = gpsTelemetry,
            depthUnit = settings.depthUnit,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, depthMeters, targetFish, notes ->
                onSaveWaypoint(name, depthMeters, targetFish, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun WaypointCard(
    waypoint: WaypointEntity,
    isSelected: Boolean,
    distanceText: String,
    bearingText: String,
    depthText: String,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
            .testTag("waypoint_card_${waypoint.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                    contentDescription = "Ikon Waypoint",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = waypoint.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LOCKED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = MarineGeoUtils.formatDecimalCoords(waypoint.latitude, waypoint.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Waves,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = depthText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "$distanceText • $bearingText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (waypoint.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = waypoint.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_waypoint_${waypoint.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Hapus Spot ${waypoint.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SaveWaypointDialog(
    gpsTelemetry: GpsTelemetry,
    depthUnit: DepthUnit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, depthMeters: Double, targetFish: String, notes: String) -> Unit
) {
    var spotName by remember { mutableStateOf("") }
    var depthInput by remember { mutableStateOf("18") }
    var targetFish by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.dialog_save_spot_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GPS: ${MarineGeoUtils.formatDecimalCoords(gpsTelemetry.latitude, gpsTelemetry.longitude)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = spotName,
                    onValueChange = {
                        spotName = it
                        showNameError = false
                    },
                    label = { Text(stringResource(R.string.label_spot_name)) },
                    isError = showNameError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("waypoint_name_input")
                )

                OutlinedTextField(
                    value = depthInput,
                    onValueChange = { depthInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("${stringResource(R.string.label_spot_depth)} (${depthUnit.shortUnit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("waypoint_depth_input")
                )

                OutlinedTextField(
                    value = targetFish,
                    onValueChange = { targetFish = it },
                    label = { Text(stringResource(R.string.label_spot_fish)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("waypoint_fish_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_spot_notes)) },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("waypoint_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (spotName.isBlank()) {
                        showNameError = true
                    } else {
                        val parsedDepth = depthInput.toDoubleOrNull() ?: 0.0
                        val depthInMeters = when (depthUnit) {
                            DepthUnit.METERS -> parsedDepth
                            DepthUnit.FEET -> parsedDepth / 3.28084
                        }
                        onConfirm(spotName, depthInMeters, targetFish, notes)
                    }
                },
                modifier = Modifier.testTag("confirm_save_waypoint_button")
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
