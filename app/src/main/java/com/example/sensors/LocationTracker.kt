package com.example.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsTelemetry(
    val latitude: Double = MarineGeoUtils.BINTAN_REF_LAT,
    val longitude: Double = MarineGeoUtils.BINTAN_REF_LON,
    val accuracyMeters: Float? = null,
    val speedKnots: Float = 0f,
    val bearingDegrees: Float? = null,
    val hasPermission: Boolean = false,
    val isLiveFix: Boolean = false,
    val lastUpdatedMillis: Long? = null,
    val providerStatus: String = "Menunggu izin GPS"
)

class LocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val _telemetry = MutableStateFlow(GpsTelemetry(hasPermission = checkLocationPermission()))
    val telemetry: StateFlow<GpsTelemetry> = _telemetry.asStateFlow()

    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            publishLocation(location)
        }
    }

    fun checkLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val granted = checkLocationPermission()
        if (!granted) {
            _telemetry.value = _telemetry.value.copy(
                hasPermission = false,
                isLiveFix = false,
                providerStatus = "Izin lokasi belum diberikan"
            )
            return
        }

        if (isTracking) return
        isTracking = true

        _telemetry.value = _telemetry.value.copy(
            hasPermission = true,
            providerStatus = "Mencari satelit GPS..."
        )

        // Bootstrap immediately with last known location if available
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    publishLocation(location)
                }
            }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3_000L
        )
            .setMinUpdateIntervalMillis(1_500L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        runCatching {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        }.onFailure { error ->
            isTracking = false
            _telemetry.value = _telemetry.value.copy(
                providerStatus = "GPS tidak tersedia: ${error.localizedMessage ?: "Error"}"
            )
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun publishLocation(location: Location) {
        _telemetry.value = GpsTelemetry(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            speedKnots = if (location.hasSpeed()) MarineGeoUtils.metersPerSecondToKnots(location.speed) else 0f,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            hasPermission = true,
            isLiveFix = true,
            lastUpdatedMillis = location.time,
            providerStatus = "GPS Aktif (${location.provider ?: "Fused"})"
        )
    }
}
