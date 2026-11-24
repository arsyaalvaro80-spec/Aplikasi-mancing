package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompassTelemetry(
    val azimuthDegrees: Float = 0f,
    val hasSensor: Boolean = false,
    val sensorSource: String = "Tidak terdeteksi",
    val accuracyLabel: String = "—"
)

class CompassSensorManager(context: Context) : SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _telemetry = MutableStateFlow(
        CompassTelemetry(
            hasSensor = rotationVectorSensor != null ||
                (accelerometerSensor != null && magnetometerSensor != null),
            sensorSource = when {
                rotationVectorSensor != null -> "Rotation Vector (9-Axis)"
                accelerometerSensor != null && magnetometerSensor != null -> "Magnetometer + Accelerometer"
                else -> "Sensor kompas tidak tersedia"
            }
        )
    )
    val telemetry: StateFlow<CompassTelemetry> = _telemetry.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var gravityReading: FloatArray? = null
    private var magneticReading: FloatArray? = null
    private var smoothedAzimuth: Float = 0f
    private var hasInitialReading = false
    private var isListening = false

    fun startListening() {
        if (isListening) return
        when {
            rotationVectorSensor != null -> {
                sensorManager.registerListener(
                    this,
                    rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                isListening = true
            }
            accelerometerSensor != null && magnetometerSensor != null -> {
                sensorManager.registerListener(
                    this,
                    accelerometerSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                sensorManager.registerListener(
                    this,
                    magnetometerSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                isListening = true
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
                updateAzimuth(rawDeg)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravityReading = event.values.clone()
                computeFallbackOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magneticReading = event.values.clone()
                computeFallbackOrientation()
            }
        }
    }

    private fun computeFallbackOrientation() {
        val gravity = gravityReading ?: return
        val magnetic = magneticReading ?: return
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val rawDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
            updateAzimuth(rawDeg)
        }
    }

    private fun updateAzimuth(rawDeg: Float) {
        smoothedAzimuth = if (!hasInitialReading) {
            hasInitialReading = true
            rawDeg
        } else {
            MarineGeoUtils.lowPassAngle(smoothedAzimuth, rawDeg, alpha = 0.2f)
        }
        _telemetry.value = _telemetry.value.copy(
            azimuthDegrees = smoothedAzimuth,
            hasSensor = true
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val label = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Tinggi"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Sedang"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Rendah (Kalibrasi 8)"
            else -> "Tidak Terkalibrasi"
        }
        _telemetry.value = _telemetry.value.copy(accuracyLabel = label)
    }
}
