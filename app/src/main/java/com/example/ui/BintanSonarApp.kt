package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.sensors.LatLon
import com.example.ui.screens.ChartplotterScreen
import com.example.ui.screens.RadarPredictionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SonarCameraScreen
import com.example.ui.screens.WaypointScreen
import com.example.viewmodel.SonarViewModel

const val ROUTE_RADAR = "radar_prediction"
const val ROUTE_MAP = "chartplotter_map"
const val ROUTE_WAYPOINTS = "waypoint_gps"
const val ROUTE_SONAR_CAMERA = "sonar_camera"
const val ROUTE_SETTINGS = "settings"

private data class SonarDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    SonarDestination(
        route = ROUTE_RADAR,
        labelRes = R.string.nav_radar,
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        testTag = "nav_tab_radar"
    ),
    SonarDestination(
        route = ROUTE_MAP,
        labelRes = R.string.nav_map,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
        testTag = "nav_tab_map"
    ),
    SonarDestination(
        route = ROUTE_WAYPOINTS,
        labelRes = R.string.nav_waypoints,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_tab_waypoints"
    ),
    SonarDestination(
        route = ROUTE_SONAR_CAMERA,
        labelRes = R.string.nav_sonar_camera,
        selectedIcon = Icons.Filled.CameraAlt,
        unselectedIcon = Icons.Outlined.CameraAlt,
        testTag = "nav_tab_camera"
    ),
    SonarDestination(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

@Composable
fun BintanSonarApp(
    viewModel: SonarViewModel,
    onKeepScreenAwakeChanged: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_RADAR

    val gpsTelemetry by viewModel.gpsTelemetry.collectAsStateWithLifecycle()
    val compassTelemetry by viewModel.compassTelemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val selectedWaypoint by viewModel.selectedWaypoint.collectAsStateWithLifecycle()
    val vesselTrack by viewModel.vesselTrack.collectAsStateWithLifecycle()
    val marineScanState by viewModel.marineScanState.collectAsStateWithLifecycle()
    val capturedBitmap by viewModel.capturedBitmap.collectAsStateWithLifecycle()
    val sonarVisionState by viewModel.sonarVisionState.collectAsStateWithLifecycle()
    val tideState by viewModel.tideState.collectAsStateWithLifecycle()

    // Lifecycle-aware GPS & Compass sensor activation
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startSensors()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopSensors()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSensors()
        }
    }

    // Sync keep screen awake setting with activity window
    DisposableEffect(settings.keepScreenAwake) {
        onKeepScreenAwakeChanged(settings.keepScreenAwake)
        onDispose { }
    }

    val onNavigate: (String) -> Unit = { route ->
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onNavigate(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val contentModifier = if (currentRoute == ROUTE_MAP) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = 700.dp)
                        }
                        SonarNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            gpsTelemetry = gpsTelemetry,
                            compassTelemetry = compassTelemetry,
                            settings = settings,
                            waypoints = waypoints,
                            selectedWaypoint = selectedWaypoint,
                            vesselTrack = vesselTrack,
                            marineScanState = marineScanState,
                            capturedBitmap = capturedBitmap,
                            sonarVisionState = sonarVisionState,
                            tideState = tideState,
                            onNavigate = onNavigate,
                            modifier = contentModifier
                        )
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onNavigate(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                SonarNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    gpsTelemetry = gpsTelemetry,
                    compassTelemetry = compassTelemetry,
                    settings = settings,
                    waypoints = waypoints,
                    selectedWaypoint = selectedWaypoint,
                    vesselTrack = vesselTrack,
                    marineScanState = marineScanState,
                    capturedBitmap = capturedBitmap,
                    sonarVisionState = sonarVisionState,
                    tideState = tideState,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun SonarNavHost(
    navController: NavHostController,
    viewModel: SonarViewModel,
    gpsTelemetry: com.example.sensors.GpsTelemetry,
    compassTelemetry: com.example.sensors.CompassTelemetry,
    settings: com.example.data.preferences.SonarSettings,
    waypoints: List<com.example.data.local.WaypointEntity>,
    selectedWaypoint: com.example.data.local.WaypointEntity?,
    vesselTrack: List<LatLon>,
    marineScanState: com.example.viewmodel.MarineScanUiState,
    capturedBitmap: android.graphics.Bitmap?,
    sonarVisionState: com.example.viewmodel.SonarVisionUiState,
    tideState: com.example.viewmodel.TideUiState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_RADAR,
        modifier = modifier
    ) {
        composable(ROUTE_RADAR) {
            RadarPredictionScreen(
                gpsTelemetry = gpsTelemetry,
                compassTelemetry = compassTelemetry,
                settings = settings,
                scanState = marineScanState,
                tideState = tideState,
                onScanMarine = {
                    viewModel.refreshTideForecast()
                    viewModel.scanMarineConditions()
                },
                onRefreshTide = viewModel::refreshTideForecast,
                onSetFishingMode = viewModel::setFishingMode,
                onLocationPermissionResult = viewModel::onLocationPermissionResult
            )
        }
        composable(ROUTE_MAP) {
            ChartplotterScreen(
                tileManager = viewModel.tileManager,
                gpsTelemetry = gpsTelemetry,
                compassTelemetry = compassTelemetry,
                settings = settings,
                waypoints = waypoints,
                selectedWaypoint = selectedWaypoint,
                vesselTrack = vesselTrack,
                onSelectWaypoint = viewModel::selectWaypoint,
                onSaveWaypointAt = viewModel::saveWaypointAt,
                onLocationPermissionResult = viewModel::onLocationPermissionResult
            )
        }
        composable(ROUTE_WAYPOINTS) {
            WaypointScreen(
                gpsTelemetry = gpsTelemetry,
                compassTelemetry = compassTelemetry,
                settings = settings,
                waypoints = waypoints,
                selectedWaypoint = selectedWaypoint,
                onSaveWaypoint = viewModel::saveCurrentSpot,
                onSelectWaypoint = viewModel::selectWaypoint,
                onDeleteWaypoint = viewModel::deleteWaypoint,
                onOpenChartplotter = { onNavigate(ROUTE_MAP) }
            )
        }
        composable(ROUTE_SONAR_CAMERA) {
            SonarCameraScreen(
                capturedBitmap = capturedBitmap,
                visionState = sonarVisionState,
                onImageSelected = viewModel::setCapturedSonarImage,
                onClearImage = viewModel::clearCapturedSonarImage,
                onAnalyzeImage = viewModel::analyzeCapturedSonarImage
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                settings = settings,
                gpsTelemetry = gpsTelemetry,
                compassTelemetry = compassTelemetry,
                onSetDepthUnit = viewModel::setDepthUnit,
                onSetDistanceUnit = viewModel::setDistanceUnit,
                onSetSonarRangeMeters = viewModel::setSonarRangeMeters,
                onSetKeepScreenAwake = viewModel::setKeepScreenAwake,
                onSetFishingMode = viewModel::setFishingMode
            )
        }
    }
}
