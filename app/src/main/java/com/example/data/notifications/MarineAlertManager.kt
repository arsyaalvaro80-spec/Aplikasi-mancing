package com.example.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.marine.TideMarineRepository
import com.example.data.marine.TideTrend
import com.example.data.preferences.SonarPreferences
import com.example.data.weather.WeatherRadarRepository
import com.example.data.weather.WeatherSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "MarineAlerts"
private const val CHANNEL_STORM_ID = "bintan_storm_alerts"
private const val CHANNEL_TIDE_ID = "bintan_tide_gacor_alerts"
private const val NOTIF_ID_STORM = 4001
private const val NOTIF_ID_TIDE = 4002
private const val NOTIF_ID_TEST = 4003
private const val JOB_ID_MARINE_ALERTS = 9101
private const val PERIODIC_INTERVAL_MS = 30L * 60L * 1000L // 30 minutes
private const val STORM_COOLDOWN_MS = 3L * 60L * 60L * 1000L // 3 hours
private const val TIDE_COOLDOWN_MS = 5L * 60L * 60L * 1000L // 5 hours

object MarineAlertManager {

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val stormChannel = NotificationChannel(
            CHANNEL_STORM_ID,
            "Peringatan Badai & Hujan Lebat",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Peringatan saat badai petir, hujan lebat, atau angin kencang mendekati spot mancing."
        }

        val tideChannel = NotificationChannel(
            CHANNEL_TIDE_ID,
            "Pengingat Jam Gacor & Pasang Naik",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Pengingat otomatis saat arus pasang naik aktif (waktu ideal ikan mencari makan)."
        }

        manager.createNotificationChannel(stormChannel)
        manager.createNotificationChannel(tideChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun syncPeriodicMonitoring(context: Context, stormEnabled: Boolean, tideEnabled: Boolean) {
        ensureChannels(context)
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (!stormEnabled && !tideEnabled) {
            scheduler.cancel(JOB_ID_MARINE_ALERTS)
            Log.d(TAG, "Marine alert monitoring cancelled.")
            return
        }

        val component = ComponentName(context.applicationContext, MarineAlertJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID_MARINE_ALERTS, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIODIC_INTERVAL_MS)
            .setPersisted(false)
            .build()

        val result = scheduler.schedule(jobInfo)
        Log.d(TAG, "Marine alert periodic job scheduled (result=$result).")
    }

    fun sendTestNotification(
        context: Context,
        stormEnabled: Boolean,
        tideEnabled: Boolean,
        tideSummary: String?,
        weatherSummary: String?
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannels(context)

        val statusParts = mutableListOf<String>()
        if (tideEnabled) statusParts.add("Jam Gacor / Pasang Naik")
        if (stormEnabled) statusParts.add("Peringatan Badai")
        val activeLabel = if (statusParts.isEmpty()) "Pengingat Mancing" else statusParts.joinToString(" & ")

        val bodyText = buildString {
            append("Aktif: $activeLabel. ")
            if (!weatherSummary.isNullOrBlank()) append("Cuaca: $weatherSummary. ")
            if (!tideSummary.isNullOrBlank()) append("Pasang: $tideSummary.")
        }.trim()

        postNotification(
            context = context,
            channelId = CHANNEL_TIDE_ID,
            notificationId = NOTIF_ID_TEST,
            title = "Pengingat Bintan Sonar AI Aktif",
            body = bodyText,
            highPriority = true
        )
        return true
    }

    fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        highPriority: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(context, notificationId, launchIntent, pendingFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }.onFailure { err ->
            Log.e(TAG, "Failed to post notification", err)
        }
    }
}

/**
 * Battery-friendly periodic background JobService (no foreground service required)
 * that checks live Open-Meteo storm risk and rising-tide gacor windows every 30 minutes.
 */
class MarineAlertJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val appContext = applicationContext
        val prefs = SonarPreferences(appContext)
        val settings = prefs.settings.value

        if ((!settings.stormAlertsEnabled && !settings.tideAlertsEnabled) ||
            !MarineAlertManager.hasNotificationPermission(appContext)
        ) {
            return false
        }

        val (lat, lon) = prefs.getLastKnownLocation()
        val nowMs = System.currentTimeMillis()

        activeJob = serviceScope.launch {
            try {
                // 1. Check incoming storms / heavy rain / strong gusts within next 3 hours
                if (settings.stormAlertsEnabled && (nowMs - prefs.getLastStormNotifiedMs()) >= STORM_COOLDOWN_MS) {
                    val weatherRepo = WeatherRadarRepository()
                    val result = weatherRepo.fetchWeatherDashboard(lat, lon)
                    result.getOrNull()?.let { data ->
                        val next3h = data.hourlyNext24h.take(3)
                        val dangerHour = next3h.firstOrNull {
                            it.severity.rank >= WeatherSeverity.HEAVY_RAIN.rank || it.windGustsKnots >= 20.0
                        }
                        val stormZones = data.regionalZones.filter { it.severity == WeatherSeverity.STORM }

                        if (data.current.severity.rank >= WeatherSeverity.HEAVY_RAIN.rank || dangerHour != null || stormZones.isNotEmpty()) {
                            val title = when {
                                data.current.severity == WeatherSeverity.STORM || dangerHour?.severity == WeatherSeverity.STORM ->
                                    "Peringatan Badai Petir di Sekitar Bintan!"
                                stormZones.isNotEmpty() ->
                                    "Badai Terpantau di ${stormZones.first().name.substringBefore(" /")}"
                                else ->
                                    "Waspada Hujan Lebat & Angin Kencang"
                            }
                            val detail = if (dangerHour != null) {
                                "${dangerHour.conditionLabel} sekitar ${dangerHour.timeLabel} (hembusan ${String.format(Locale.US, "%.0f", dangerHour.windGustsKnots)} kn). Cek Peta Radar Badai sebelum melaut/ke karang."
                            } else {
                                data.safetyAdvice
                            }
                            MarineAlertManager.postNotification(
                                context = appContext,
                                channelId = CHANNEL_STORM_ID,
                                notificationId = NOTIF_ID_STORM,
                                title = title,
                                body = detail,
                                highPriority = true
                            )
                            prefs.setLastStormNotifiedMs(nowMs)
                        }
                    }
                }

                // 2. Check active rising tide (Pasang Naik / Jam Gacor window)
                if (settings.tideAlertsEnabled && (nowMs - prefs.getLastTideNotifiedMs()) >= TIDE_COOLDOWN_MS) {
                    val tideRepo = TideMarineRepository()
                    val tide = tideRepo.fetchTideForecast(lat, lon)
                    val nextHigh = tide.extremes.firstOrNull { it.isHighTide && it.hourOfDay >= tide.currentHourFraction }
                    val isPrimeRising = tide.currentTrend == TideTrend.RISING && tide.rateCmPerHour >= 8.0

                    if (isPrimeRising) {
                        val peakInfo = nextHigh?.let {
                            " Puncak pasang sekitar ${it.timeLabel} (${String.format(Locale.US, "%+.2f", it.heightMeters)}m)."
                        } ?: ""
                        MarineAlertManager.postNotification(
                            context = appContext,
                            channelId = CHANNEL_TIDE_ID,
                            notificationId = NOTIF_ID_TIDE,
                            title = "Jam Gacor! Air Sedang Pasang Naik",
                            body = "Arus pasang aktif (+${String.format(Locale.US, "%.0f", tide.rateCmPerHour)} cm/jam) — ikan karang & pinggiran mulai masuk mencari makan.$peakInfo",
                            highPriority = false
                        )
                        prefs.setLastTideNotifiedMs(nowMs)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background marine alert check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
