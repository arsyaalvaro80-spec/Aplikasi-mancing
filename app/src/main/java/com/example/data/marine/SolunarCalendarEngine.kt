package com.example.data.marine

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

enum class TidalRegime(val label: String, val advice: String) {
    SPRING_TIDE(
        label = "Pasang Besar",
        advice = "Arus deras & beda pasang-surut maksimal — ikan predator aktif berburu di tubir & pinggir."
    ),
    MODERATE_TIDE(
        label = "Pasang Sedang",
        advice = "Arus seimbang & stabil — bagus untuk casting pinggir maupun dasaran karang."
    ),
    NEAP_TIDE(
        label = "Pasang Perbani",
        advice = "Arus lebih tenang — ideal untuk mancing dasaran karang dalam atau air jernih pinggir."
    )
}

data class LunarPhaseInfo(
    val ageDays: Double,
    val illuminationPercent: Int,
    val phaseAngleRad: Double,
    val phaseName: String,
    val tidalRegime: TidalRegime,
    val majorWindows: List<String>,
    val minorWindows: List<String>,
    val biteScore: Int,
    val biteLabel: String
)

data class DayTideAndSolunar(
    val dayIndex: Int,
    val dateLabel: String,
    val shortDayLabel: String,
    val lunarInfo: LunarPhaseInfo,
    val maxHighMeters: Double,
    val minLowMeters: Double,
    val peakHighSummary: String,
    val peakLowSummary: String,
    val maxWaveMeters: Double,
    val hourlyPoints: List<TideHourPoint>
)

/**
 * Pure astronomical Lunar Phase, Spring/Neap Tidal Regime, and Solunar Major/Minor window engine.
 */
object SolunarCalendarEngine {
    private const val SYNODIC_MONTH_DAYS = 29.53058867
    // Known astronomical New Moon epoch: 2000-01-06 18:14 UTC
    private const val EPOCH_NEW_MOON_MS = 947182440000L

    fun computeLunarPhase(calendar: Calendar, longitudeDeg: Double): LunarPhaseInfo {
        // Use local noon on the requested calendar date for consistent daily phase readout
        val cal = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffDays = (cal.timeInMillis - EPOCH_NEW_MOON_MS) / 86_400_000.0
        val rawMod = diffDays % SYNODIC_MONTH_DAYS
        val ageDays = if (rawMod < 0.0) rawMod + SYNODIC_MONTH_DAYS else rawMod
        val phaseAngleRad = (ageDays / SYNODIC_MONTH_DAYS) * 2.0 * PI
        val illumination = (((1.0 - cos(phaseAngleRad)) * 0.5) * 100.0).roundToInt().coerceIn(0, 100)

        val phaseName = when {
            ageDays < 1.5 || ageDays >= 28.0 -> "Bulan Baru (Mati)"
            ageDays < 6.4 -> "Sabit Awal"
            ageDays < 8.4 -> "Perempat Awal"
            ageDays < 13.3 -> "Cembung Awal"
            ageDays < 16.3 -> "Bulan Purnama"
            ageDays < 21.2 -> "Cembung Akhir"
            ageDays < 23.2 -> "Perempat Akhir"
            else -> "Sabit Akhir"
        }

        val tidalRegime = when {
            ageDays <= 2.6 || ageDays >= 26.9 || ageDays in 12.4..17.1 -> TidalRegime.SPRING_TIDE
            ageDays in 5.4..9.4 || ageDays in 19.9..24.1 -> TidalRegime.NEAP_TIDE
            else -> TidalRegime.MODERATE_TIDE
        }

        // Lunar transit (moon overhead) advances ~48.8 minutes per lunar day starting at 12:00 on New Moon
        val transitHour = (12.0 + (ageDays * 0.8128) - ((longitudeDeg % 15.0) * 0.03)) % 24.0
        val underfootHour = (transitHour + 12.4) % 24.0
        val moonriseHour = (transitHour - 6.1 + 24.0) % 24.0
        val moonsetHour = (transitHour + 6.1) % 24.0

        val majorWindows = listOf(
            formatHourWindow(transitHour, durationHours = 2.0),
            formatHourWindow(underfootHour, durationHours = 2.0)
        )
        val minorWindows = listOf(
            formatHourWindow(moonriseHour, durationHours = 1.0),
            formatHourWindow(moonsetHour, durationHours = 1.0)
        )

        val biteScore = when (tidalRegime) {
            TidalRegime.SPRING_TIDE -> when {
                ageDays < 1.8 || ageDays > 27.7 || ageDays in 13.8..15.8 -> 92
                else -> 84
            }
            TidalRegime.MODERATE_TIDE -> 74
            TidalRegime.NEAP_TIDE -> 62
        }
        val biteLabel = when {
            biteScore >= 85 -> "SANGAT GACOR"
            biteScore >= 72 -> "POTENSIAL"
            else -> "TENANG"
        }

        return LunarPhaseInfo(
            ageDays = ageDays,
            illuminationPercent = illumination,
            phaseAngleRad = phaseAngleRad,
            phaseName = phaseName,
            tidalRegime = tidalRegime,
            majorWindows = majorWindows,
            minorWindows = minorWindows,
            biteScore = biteScore,
            biteLabel = biteLabel
        )
    }

    fun buildSolunarSummaryForDay(
        dayCal: Calendar,
        dayIndex: Int,
        longitudeDeg: Double,
        hourlyPoints: List<TideHourPoint>
    ): DayTideAndSolunar {
        val lunarInfo = computeLunarPhase(dayCal, longitudeDeg)
        val localeId = Locale.forLanguageTag("id-ID")
        val dayFmt = SimpleDateFormat("EEE", localeId)
        val dateFmt = SimpleDateFormat("dd MMM", localeId)

        val shortDayLabel = when (dayIndex) {
            0 -> "Hari Ini"
            1 -> "Besok"
            else -> dayFmt.format(dayCal.time).replace(".", "")
        }
        val dateLabel = "$shortDayLabel • ${dateFmt.format(dayCal.time)}"

        val highs = mutableListOf<TideHourPoint>()
        val lows = mutableListOf<TideHourPoint>()
        for (i in 1 until hourlyPoints.lastIndex) {
            val prev = hourlyPoints[i - 1].seaLevelMeters
            val curr = hourlyPoints[i].seaLevelMeters
            val next = hourlyPoints[i + 1].seaLevelMeters
            if (curr >= prev && curr > next) highs.add(hourlyPoints[i])
            else if (curr <= prev && curr < next) lows.add(hourlyPoints[i])
        }

        val maxPt = hourlyPoints.maxByOrNull { it.seaLevelMeters }
        val minPt = hourlyPoints.minByOrNull { it.seaLevelMeters }
        if (highs.isEmpty() && maxPt != null) highs.add(maxPt)
        if (lows.isEmpty() && minPt != null) lows.add(minPt)

        val peakHighSummary = highs.take(2).joinToString(" & ") { pt ->
            "${pt.label} (${String.format(Locale.US, "%+.1fm", pt.seaLevelMeters)})"
        }
        val peakLowSummary = lows.take(2).joinToString(" & ") { pt ->
            "${pt.label} (${String.format(Locale.US, "%+.1fm", pt.seaLevelMeters)})"
        }

        val maxWave = hourlyPoints.maxOfOrNull { it.waveHeightMeters } ?: 0.5

        return DayTideAndSolunar(
            dayIndex = dayIndex,
            dateLabel = dateLabel,
            shortDayLabel = shortDayLabel,
            lunarInfo = lunarInfo,
            maxHighMeters = maxPt?.seaLevelMeters ?: 0.0,
            minLowMeters = minPt?.seaLevelMeters ?: 0.0,
            peakHighSummary = peakHighSummary,
            peakLowSummary = peakLowSummary,
            maxWaveMeters = maxWave,
            hourlyPoints = hourlyPoints
        )
    }

    private fun formatHourWindow(centerHour: Double, durationHours: Double): String {
        val half = durationHours / 2.0
        val start = ((centerHour - half) % 24.0 + 24.0) % 24.0
        val end = ((centerHour + half) % 24.0 + 24.0) % 24.0
        return "${formatHourMinute(start)}–${formatHourMinute(end)}"
    }

    private fun formatHourMinute(hourFloat: Double): String {
        val totalMinutes = ((hourFloat * 60.0).roundToInt() % 1440 + 1440) % 1440
        val h = totalMinutes / 60
        val m = (totalMinutes % 60 / 5) * 5
        return String.format(Locale.US, "%02d:%02d", h, m)
    }
}
