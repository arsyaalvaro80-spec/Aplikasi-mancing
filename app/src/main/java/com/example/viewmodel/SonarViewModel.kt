package com.example.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.MarinePrediction
import com.example.data.ai.SonarAiRepository
import com.example.data.ai.SonarVisionAnalysis
import com.example.data.local.WaypointEntity
import com.example.data.local.WaypointRepository
import com.example.data.preferences.DepthUnit
import com.example.data.preferences.DistanceUnit
import com.example.data.preferences.FishingMode
import com.example.data.preferences.SonarPreferences
import com.example.data.preferences.SonarSettings
import com.example.data.map.MarineTileManager
import com.example.data.marine.TideForecastData
import com.example.data.marine.TideMarineRepository
import com.example.sensors.CompassSensorManager
import com.example.sensors.CompassTelemetry
import com.example.sensors.GpsTelemetry
import com.example.sensors.LatLon
import com.example.sensors.LocationTracker
import com.example.sensors.MarineGeoUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MarineScanUiState {
    data object Idle : MarineScanUiState
    data object Loading : MarineScanUiState
    data class Success(val prediction: MarinePrediction) : MarineScanUiState
    data class Error(val message: String) : MarineScanUiState
}

sealed interface SonarVisionUiState {
    data object Idle : SonarVisionUiState
    data object Loading : SonarVisionUiState
    data class Success(val analysis: SonarVisionAnalysis) : SonarVisionUiState
    data class Error(val message: String) : SonarVisionUiState
}

sealed interface TideUiState {
    data object Loading : TideUiState
    data class Ready(val data: TideForecastData) : TideUiState
    data class Error(val message: String) : TideUiState
}

class SonarViewModel(
    private val waypointRepository: WaypointRepository,
    private val preferences: SonarPreferences,
    private val aiRepository: SonarAiRepository,
    private val locationTracker: LocationTracker,
    private val compassSensorManager: CompassSensorManager,
    val tileManager: MarineTileManager,
    private val tideRepository: TideMarineRepository
) : ViewModel() {

    val gpsTelemetry: StateFlow<GpsTelemetry> = locationTracker.telemetry
    val compassTelemetry: StateFlow<CompassTelemetry> = compassSensorManager.telemetry
    val settings: StateFlow<SonarSettings> = preferences.settings

    private val _vesselTrack = MutableStateFlow<List<LatLon>>(emptyList())
    val vesselTrack: StateFlow<List<LatLon>> = _vesselTrack.asStateFlow()

    val waypoints: StateFlow<List<WaypointEntity>> = waypointRepository.allWaypoints
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _selectedWaypointId = MutableStateFlow<Long?>(null)
    val selectedWaypoint: StateFlow<WaypointEntity?> = combine(
        waypoints,
        _selectedWaypointId
    ) { list, selectedId ->
        list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null
    )

    private val _marineScanState = MutableStateFlow<MarineScanUiState>(MarineScanUiState.Idle)
    val marineScanState: StateFlow<MarineScanUiState> = _marineScanState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _sonarVisionState = MutableStateFlow<SonarVisionUiState>(SonarVisionUiState.Idle)
    val sonarVisionState: StateFlow<SonarVisionUiState> = _sonarVisionState.asStateFlow()

    private val _tideState = MutableStateFlow<TideUiState>(TideUiState.Loading)
    val tideState: StateFlow<TideUiState> = _tideState.asStateFlow()

    init {
        refreshTideForecast()
        // Record live GPS breadcrumb track when vessel moves more than 5 meters
        viewModelScope.launch {
            gpsTelemetry.collect { gps ->
                if (!gps.isLiveFix) return@collect
                val current = _vesselTrack.value
                val last = current.lastOrNull()
                val shouldAppend = last == null || MarineGeoUtils.distanceMeters(
                    last.latitude,
                    last.longitude,
                    gps.latitude,
                    gps.longitude
                ) >= 5.0
                if (shouldAppend) {
                    val updated = (current + LatLon(gps.latitude, gps.longitude)).takeLast(300)
                    _vesselTrack.value = updated
                }
            }
        }
    }

    fun refreshTideForecast() {
        viewModelScope.launch {
            _tideState.value = TideUiState.Loading
            val gps = gpsTelemetry.value
            try {
                val data = tideRepository.fetchTideForecast(
                    latitude = gps.latitude,
                    longitude = gps.longitude
                )
                _tideState.value = TideUiState.Ready(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _tideState.value = TideUiState.Error(
                    e.localizedMessage ?: "Gagal memuat data pasang surut."
                )
            }
        }
    }

    fun startSensors() {
        locationTracker.startTracking()
        compassSensorManager.startListening()
    }

    fun stopSensors() {
        locationTracker.stopTracking()
        compassSensorManager.stopListening()
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            locationTracker.startTracking()
        }
    }

    fun clearVesselTrack() {
        _vesselTrack.value = emptyList()
    }

    fun scanMarineConditions() {
        viewModelScope.launch {
            _marineScanState.value = MarineScanUiState.Loading
            val gps = gpsTelemetry.value
            val liveTideSummary = (tideState.value as? TideUiState.Ready)?.data?.let { tide ->
                "Tren=${tide.currentTrend.label}, TinggiAir=${String.format(java.util.Locale.US, "%+.2f", tide.currentHeightMeters)}m, " +
                    "Ombak=${String.format(java.util.Locale.US, "%.1f", tide.currentWaveMeters)}m (${tide.currentWaveDirection}), " +
                    "Arus=${String.format(java.util.Locale.US, "%.1f", tide.currentSpeedKnots)} knot, " +
                    "SuhuAir=${String.format(java.util.Locale.US, "%.1f", tide.seaTempCelsius)}°C"
            }
            try {
                val result = aiRepository.analyzeMarineConditions(
                    latitude = gps.latitude,
                    longitude = gps.longitude,
                    accuracyMeters = gps.accuracyMeters,
                    liveTideContext = liveTideSummary,
                    fishingMode = settings.value.fishingMode
                )
                result.fold(
                    onSuccess = { prediction ->
                        _marineScanState.value = MarineScanUiState.Success(prediction)
                    },
                    onFailure = { error ->
                        _marineScanState.value = MarineScanUiState.Error(
                            error.localizedMessage ?: "Gagal memindai kondisi laut."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _marineScanState.value = MarineScanUiState.Error(
                    e.localizedMessage ?: "Terjadi kesalahan saat analisis."
                )
            }
        }
    }

    fun saveCurrentSpot(
        name: String,
        depthMeters: Double,
        targetFish: String,
        notes: String
    ) {
        val gps = gpsTelemetry.value
        saveWaypointAt(
            name = name,
            latitude = gps.latitude,
            longitude = gps.longitude,
            depthMeters = depthMeters,
            targetFish = targetFish,
            notes = notes
        )
    }

    fun saveWaypointAt(
        name: String,
        latitude: Double,
        longitude: Double,
        depthMeters: Double,
        targetFish: String,
        notes: String
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val newId = waypointRepository.saveWaypoint(
                WaypointEntity(
                    name = cleanName,
                    latitude = latitude,
                    longitude = longitude,
                    depthMeters = depthMeters.coerceAtLeast(0.0),
                    targetFish = targetFish.trim(),
                    notes = notes.trim()
                )
            )
            _selectedWaypointId.value = newId
        }
    }

    fun selectWaypoint(id: Long) {
        _selectedWaypointId.value = id
    }

    fun deleteWaypoint(id: Long) {
        viewModelScope.launch {
            waypointRepository.deleteWaypoint(id)
            if (_selectedWaypointId.value == id) {
                _selectedWaypointId.value = null
            }
        }
    }

    fun setCapturedSonarImage(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _sonarVisionState.value = SonarVisionUiState.Idle
    }

    fun clearCapturedSonarImage() {
        _capturedBitmap.value = null
        _sonarVisionState.value = SonarVisionUiState.Idle
    }

    fun analyzeCapturedSonarImage() {
        val bitmap = _capturedBitmap.value ?: return
        viewModelScope.launch {
            _sonarVisionState.value = SonarVisionUiState.Loading
            val gps = gpsTelemetry.value
            try {
                val result = aiRepository.analyzeSonarImage(
                    bitmap = bitmap,
                    latitude = gps.latitude,
                    longitude = gps.longitude,
                    fishingMode = settings.value.fishingMode
                )
                result.fold(
                    onSuccess = { analysis ->
                        _sonarVisionState.value = SonarVisionUiState.Success(analysis)
                    },
                    onFailure = { error ->
                        _sonarVisionState.value = SonarVisionUiState.Error(
                            error.localizedMessage ?: "Gagal menganalisis citra sonar."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sonarVisionState.value = SonarVisionUiState.Error(
                    e.localizedMessage ?: "Terjadi kesalahan saat membaca gambar."
                )
            }
        }
    }

    fun saveApiKey(apiKey: String) = preferences.saveApiKey(apiKey)
    fun clearApiKey() = preferences.clearApiKey()
    fun setDepthUnit(unit: DepthUnit) = preferences.setDepthUnit(unit)
    fun setDistanceUnit(unit: DistanceUnit) = preferences.setDistanceUnit(unit)
    fun setSonarRangeMeters(range: Int) = preferences.setSonarRangeMeters(range)
    fun setKeepScreenAwake(enabled: Boolean) = preferences.setKeepScreenAwake(enabled)
    fun setFishingMode(mode: FishingMode) = preferences.setFishingMode(mode)

    override fun onCleared() {
        super.onCleared()
        stopSensors()
    }

    class Factory(
        private val waypointRepository: WaypointRepository,
        private val preferences: SonarPreferences,
        private val aiRepository: SonarAiRepository,
        private val locationTracker: LocationTracker,
        private val compassSensorManager: CompassSensorManager,
        private val tileManager: MarineTileManager,
        private val tideRepository: TideMarineRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SonarViewModel::class.java))
            return SonarViewModel(
                waypointRepository = waypointRepository,
                preferences = preferences,
                aiRepository = aiRepository,
                locationTracker = locationTracker,
                compassSensorManager = compassSensorManager,
                tileManager = tileManager,
                tideRepository = tideRepository
            ) as T
        }
    }
}
