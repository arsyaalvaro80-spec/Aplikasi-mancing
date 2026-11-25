package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DepthUnit(val label: String, val shortUnit: String) {
    METERS("Meter (m)", "m"),
    FEET("Kaki (ft)", "ft")
}

enum class DistanceUnit(val label: String, val shortUnit: String) {
    KILOMETERS("Kilometer (km)", "km"),
    NAUTICAL_MILES("Mil Laut (NM)", "NM")
}

enum class FishingMode(val label: String, val shortLabel: String) {
    SHORE("Mancing Pinggir (Pantai / Dermaga)", "Mancing Pinggir"),
    BOAT("Perahu / Laut Dalam", "Perahu / Laut")
}

enum class AiModelPreference(val label: String, val modelId: String?) {
    AUTO("Otomatis (4 Model)", null),
    FLASH_LITE_35("Gemini 3.5 Flash-Lite", "gemini-3.5-flash-lite"),
    FLASH_LITE_31("Gemini 3.1 Flash-Lite", "gemini-3.1-flash-lite"),
    FLASH_38("Gemini 3.8 Flash", "gemini-3.8-flash"),
    FLASH_3_PREVIEW("Gemini 3 Flash Preview", "gemini-3-flash-preview")
}

data class SonarSettings(
    val geminiApiKey: String = "",
    val depthUnit: DepthUnit = DepthUnit.METERS,
    val distanceUnit: DistanceUnit = DistanceUnit.NAUTICAL_MILES,
    val sonarRangeMeters: Int = 60,
    val keepScreenAwake: Boolean = true,
    val fishingMode: FishingMode = FishingMode.SHORE,
    val aiModelPreference: AiModelPreference = AiModelPreference.AUTO,
    val stormAlertsEnabled: Boolean = false,
    val tideAlertsEnabled: Boolean = false
)

class SonarPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SonarSettings> = _settings.asStateFlow()

    private fun loadSettings(): SonarSettings {
        val depthName = prefs.getString(KEY_DEPTH_UNIT, DepthUnit.METERS.name) ?: DepthUnit.METERS.name
        val distName = prefs.getString(KEY_DISTANCE_UNIT, DistanceUnit.NAUTICAL_MILES.name) ?: DistanceUnit.NAUTICAL_MILES.name
        val modeName = prefs.getString(KEY_FISHING_MODE, FishingMode.SHORE.name) ?: FishingMode.SHORE.name
        val modelName = prefs.getString(KEY_AI_MODEL_PREFERENCE, AiModelPreference.AUTO.name) ?: AiModelPreference.AUTO.name
        return SonarSettings(
            geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: "",
            depthUnit = runCatching { DepthUnit.valueOf(depthName) }.getOrDefault(DepthUnit.METERS),
            distanceUnit = runCatching { DistanceUnit.valueOf(distName) }.getOrDefault(DistanceUnit.NAUTICAL_MILES),
            sonarRangeMeters = prefs.getInt(KEY_SONAR_RANGE, 60).coerceIn(30, 120),
            keepScreenAwake = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true),
            fishingMode = runCatching { FishingMode.valueOf(modeName) }.getOrDefault(FishingMode.SHORE),
            aiModelPreference = runCatching { AiModelPreference.valueOf(modelName) }.getOrDefault(AiModelPreference.AUTO),
            stormAlertsEnabled = prefs.getBoolean(KEY_STORM_ALERTS, false),
            tideAlertsEnabled = prefs.getBoolean(KEY_TIDE_ALERTS, false)
        )
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        prefs.edit().putString(KEY_GEMINI_API_KEY, trimmed).apply()
        _settings.value = _settings.value.copy(geminiApiKey = trimmed)
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        _settings.value = _settings.value.copy(geminiApiKey = "")
    }

    fun setDepthUnit(unit: DepthUnit) {
        prefs.edit().putString(KEY_DEPTH_UNIT, unit.name).apply()
        _settings.value = _settings.value.copy(depthUnit = unit)
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        prefs.edit().putString(KEY_DISTANCE_UNIT, unit.name).apply()
        _settings.value = _settings.value.copy(distanceUnit = unit)
    }

    fun setSonarRangeMeters(rangeMeters: Int) {
        val clamped = rangeMeters.coerceIn(30, 120)
        prefs.edit().putInt(KEY_SONAR_RANGE, clamped).apply()
        _settings.value = _settings.value.copy(sonarRangeMeters = clamped)
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled).apply()
        _settings.value = _settings.value.copy(keepScreenAwake = enabled)
    }

    fun setFishingMode(mode: FishingMode) {
        prefs.edit().putString(KEY_FISHING_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(fishingMode = mode)
    }

    fun setAiModelPreference(preference: AiModelPreference) {
        prefs.edit().putString(KEY_AI_MODEL_PREFERENCE, preference.name).apply()
        _settings.value = _settings.value.copy(aiModelPreference = preference)
    }

    fun setStormAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STORM_ALERTS, enabled).apply()
        _settings.value = _settings.value.copy(stormAlertsEnabled = enabled)
    }

    fun setTideAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIDE_ALERTS, enabled).apply()
        _settings.value = _settings.value.copy(tideAlertsEnabled = enabled)
    }

    fun saveLastKnownLocation(latitude: Double, longitude: Double) {
        prefs.edit()
            .putString(KEY_LAST_LAT, latitude.toString())
            .putString(KEY_LAST_LON, longitude.toString())
            .apply()
    }

    fun getLastKnownLocation(): Pair<Double, Double> {
        val lat = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull() ?: 1.1480
        val lon = prefs.getString(KEY_LAST_LON, null)?.toDoubleOrNull() ?: 104.6480
        return lat to lon
    }

    fun getLastStormNotifiedMs(): Long = prefs.getLong(KEY_LAST_STORM_ALERT_MS, 0L)
    fun setLastStormNotifiedMs(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_STORM_ALERT_MS, timestampMs).apply()
    }

    fun getLastTideNotifiedMs(): Long = prefs.getLong(KEY_LAST_TIDE_ALERT_MS, 0L)
    fun setLastTideNotifiedMs(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_TIDE_ALERT_MS, timestampMs).apply()
    }

    companion object {
        private const val PREFS_NAME = "bintan_sonar_preferences"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_DEPTH_UNIT = "depth_unit"
        private const val KEY_DISTANCE_UNIT = "distance_unit"
        private const val KEY_SONAR_RANGE = "sonar_range_meters"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_FISHING_MODE = "fishing_mode"
        private const val KEY_AI_MODEL_PREFERENCE = "ai_model_preference"
        private const val KEY_STORM_ALERTS = "storm_alerts_enabled"
        private const val KEY_TIDE_ALERTS = "tide_alerts_enabled"
        private const val KEY_LAST_LAT = "last_known_lat"
        private const val KEY_LAST_LON = "last_known_lon"
        private const val KEY_LAST_STORM_ALERT_MS = "last_storm_alert_ms"
        private const val KEY_LAST_TIDE_ALERT_MS = "last_tide_alert_ms"
    }
}
