package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.marine.TideForecastData
import com.example.data.marine.TideTrend
import com.example.viewmodel.TideUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TideWaveChartCard(
    tideState: TideUiState,
    onRefreshTide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("tide_wave_chart_card")
    ) {
        when (tideState) {
            is TideUiState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Memuat kurva pasang surut & gelombang laut...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is TideUiState.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tideState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefreshTide, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Muat ulang pasang surut",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            is TideUiState.Ready -> {
                TideForecastContent(
                    data = tideState.data,
                    onRefreshTide = onRefreshTide
                )
            }
        }
    }
}

@Composable
private fun TideForecastContent(
    data: TideForecastData,
    onRefreshTide: () -> Unit
) {
    var inspectedHour by remember(data) { mutableStateOf<Int?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val trendColor = when (data.currentTrend) {
        TideTrend.RISING, TideTrend.HIGH_SLACK -> primaryColor
        TideTrend.FALLING, TideTrend.LOW_SLACK -> tertiaryColor
    }
    val trendContainer = when (data.currentTrend) {
        TideTrend.RISING, TideTrend.HIGH_SLACK -> MaterialTheme.colorScheme.primaryContainer
        TideTrend.FALLING, TideTrend.LOW_SLACK -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val activePoint = inspectedHour?.let { hr ->
        data.hourlyPoints.getOrNull(hr.coerceIn(0, data.hourlyPoints.lastIndex))
    }

    val displayHeight = activePoint?.seaLevelMeters ?: data.currentHeightMeters
    val displayWave = activePoint?.waveHeightMeters ?: data.currentWaveMeters
    val displayCurrentKn = activePoint?.currentKnots ?: data.currentSpeedKnots
    val displayTemp = activePoint?.seaTempCelsius ?: data.seaTempCelsius
    val displayTimeLabel = activePoint?.let { "JAM ${it.label} (GESER)" } ?: "SAAT INI"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row: Title + Trend Pill + Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Waves,
                        contentDescription = null,
                        tint = secondaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GELOMBANG PASANG SURUT 24 JAM",
                        style = MaterialTheme.typography.titleSmall,
                        color = textPrimary
                    )
                }
                Text(
                    text = data.sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (data.isLiveApiData) primaryColor else tertiaryColor
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(trendContainer)
                        .border(1.dp, trendColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (data.currentTrend == TideTrend.RISING || data.currentTrend == TideTrend.HIGH_SLACK) {
                            Icons.Rounded.ArrowUpward
                        } else {
                            Icons.Rounded.ArrowDownward
                        },
                        contentDescription = null,
                        tint = trendColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = data.currentTrend.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = trendColor
                    )
                }
                IconButton(
                    onClick = onRefreshTide,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("refresh_tide_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Perbarui grafik pasang surut",
                        tint = secondaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Compact Live / Scrubbed Sea Telemetry Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TideStatItem(
                label = displayTimeLabel,
                value = String.format(Locale.US, "%+.2f m", displayHeight),
                valueColor = if (displayHeight >= 0) primaryColor else tertiaryColor
            )
            TideStatItem(
                label = "OMBAK",
                value = String.format(Locale.US, "%.1f m", displayWave),
                valueColor = secondaryColor
            )
            TideStatItem(
                label = "ARUS",
                value = String.format(Locale.US, "%.1f kn", displayCurrentKn),
                valueColor = textPrimary
            )
            TideStatItem(
                label = "SUHU AIR",
                value = String.format(Locale.US, "%.1f°C", displayTemp),
                valueColor = secondaryColor
            )
        }

        // Interactive 24-Hour Sine-Wave Tide Curve Canvas
        val axisTextPaint = remember(textSecondary) {
            Paint().apply {
                isAntiAlias = true
                color = textSecondary.toArgb()
                textSize = 26f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
        }
        val peakTextPaint = remember(primaryColor) {
            Paint().apply {
                isAntiAlias = true
                color = primaryColor.toArgb()
                textSize = 25f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
        }
        val troughTextPaint = remember(tertiaryColor) {
            Paint().apply {
                isAntiAlias = true
                color = tertiaryColor.toArgb()
                textSize = 25f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, gridColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .semantics {
                    contentDescription = "Grafik gelombang pasang surut 24 jam, geser untuk cek per jam"
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(data.hourlyPoints) {
                        detectTapGestures { offset ->
                            val leftPad = 42.dp.toPx()
                            val rightPad = 14.dp.toPx()
                            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                            val relX = ((offset.x - leftPad) / chartW).coerceIn(0f, 1f)
                            inspectedHour = (relX * 23f).roundToInt().coerceIn(0, 23)
                        }
                    }
                    .pointerInput(data.hourlyPoints) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, _ ->
                                val leftPad = 42.dp.toPx()
                                val rightPad = 14.dp.toPx()
                                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                val relX = ((change.position.x - leftPad) / chartW).coerceIn(0f, 1f)
                                inspectedHour = (relX * 23f).roundToInt().coerceIn(0, 23)
                            }
                        )
                    }
            ) {
                val points = data.hourlyPoints
                if (points.size < 2) return@Canvas

                val leftPad = 42.dp.toPx()
                val rightPad = 14.dp.toPx()
                val topPad = 24.dp.toPx()
                val bottomPad = 28.dp.toPx()
                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                val minH = (points.minOf { it.seaLevelMeters } - 0.25).coerceAtMost(-0.4)
                val maxH = (points.maxOf { it.seaLevelMeters } + 0.25).coerceAtLeast(0.4)
                val rangeH = (maxH - minH).coerceAtLeast(0.5)

                fun xForHour(hourFrac: Float): Float = leftPad + (hourFrac / 23f) * chartW
                fun yForHeight(h: Double): Float {
                    val norm = ((h - minH) / rangeH).toFloat().coerceIn(0f, 1f)
                    return topPad + chartH * (1f - norm)
                }

                // Horizontal reference grid lines (Max, 0.0m MSL, Min)
                val zeroY = yForHeight(0.0)
                drawLine(
                    color = gridColor.copy(alpha = 0.8f),
                    start = Offset(leftPad, zeroY),
                    end = Offset(leftPad + chartW, zeroY),
                    strokeWidth = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "0.0m",
                    4.dp.toPx(),
                    zeroY + 4.dp.toPx(),
                    axisTextPaint
                )

                val topLabel = String.format(Locale.US, "%+.1f", maxH)
                val botLabel = String.format(Locale.US, "%+.1f", minH)
                drawContext.canvas.nativeCanvas.drawText(topLabel, 4.dp.toPx(), topPad + 4.dp.toPx(), axisTextPaint)
                drawContext.canvas.nativeCanvas.drawText(botLabel, 4.dp.toPx(), topPad + chartH, axisTextPaint)

                // Vertical hour guides every 6 hours (00, 06, 12, 18, 23)
                listOf(0, 6, 12, 18, 23).forEach { hr ->
                    val gx = xForHour(hr.toFloat())
                    drawLine(
                        color = gridColor.copy(alpha = 0.38f),
                        start = Offset(gx, topPad),
                        end = Offset(gx, topPad + chartH),
                        strokeWidth = 1f
                    )
                    val hrLabel = String.format(Locale.US, "%02d", hr)
                    drawContext.canvas.nativeCanvas.drawText(
                        hrLabel,
                        (gx - 10.dp.toPx()).coerceAtLeast(leftPad),
                        size.height - 6.dp.toPx(),
                        axisTextPaint
                    )
                }

                // Build smooth cubic Bezier wave curve
                val screenPoints = points.map { pt ->
                    Offset(xForHour(pt.hourOfDay.toFloat()), yForHeight(pt.seaLevelMeters))
                }
                val linePath = Path().apply {
                    moveTo(screenPoints.first().x, screenPoints.first().y)
                    for (i in 0 until screenPoints.lastIndex) {
                        val p0 = screenPoints[i]
                        val p1 = screenPoints[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(screenPoints.last().x, topPad + chartH)
                    lineTo(screenPoints.first().x, topPad + chartH)
                    close()
                }

                // Glowing ocean fill gradient under wave
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.42f),
                            primaryColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        startY = topPad,
                        endY = topPad + chartH
                    )
                )

                // Soft outer wave glow + crisp main wave line
                drawPath(
                    path = linePath,
                    color = secondaryColor.copy(alpha = 0.25f),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = linePath,
                    color = secondaryColor,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Plot High Tide (PASANG) and Low Tide (SURUT) crests & valleys
                data.extremes.forEach { ev ->
                    val ex = xForHour(ev.hourOfDay.toFloat())
                    val ey = yForHeight(ev.heightMeters)
                    val dotColor = if (ev.isHighTide) primaryColor else tertiaryColor
                    drawCircle(color = dotColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = Offset(ex, ey))
                    drawCircle(color = dotColor, radius = 3.8.dp.toPx(), center = Offset(ex, ey))

                    val label = String.format(Locale.US, "%+.1fm", ev.heightMeters)
                    val labelY = if (ev.isHighTide) (ey - 9.dp.toPx()).coerceAtLeast(14.dp.toPx()) else (ey + 16.dp.toPx()).coerceAtMost(topPad + chartH - 2.dp.toPx())
                    val labelX = (ex - 18.dp.toPx()).coerceIn(leftPad, leftPad + chartW - 44.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        labelX,
                        labelY,
                        if (ev.isHighTide) peakTextPaint else troughTextPaint
                    )
                }

                // Current Time ("SEKARANG") vertical cursor & pulsing dot
                val nowX = xForHour(data.currentHourFraction)
                val nowY = yForHeight(data.currentHeightMeters)
                drawLine(
                    color = primaryColor.copy(alpha = 0.85f),
                    start = Offset(nowX, topPad),
                    end = Offset(nowX, topPad + chartH),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )
                drawCircle(color = primaryColor.copy(alpha = 0.30f), radius = 10.dp.toPx(), center = Offset(nowX, nowY))
                drawCircle(color = primaryColor, radius = 4.8.dp.toPx(), center = Offset(nowX, nowY))

                // User-scrubbed inspection cursor (if touched)
                val inspectHr = inspectedHour
                if (inspectHr != null) {
                    val pt = points[inspectHr.coerceIn(0, points.lastIndex)]
                    val ix = xForHour(pt.hourOfDay.toFloat())
                    val iy = yForHeight(pt.seaLevelMeters)
                    drawLine(
                        color = textPrimary.copy(alpha = 0.7f),
                        start = Offset(ix, topPad),
                        end = Offset(ix, topPad + chartH),
                        strokeWidth = 1.6f
                    )
                    drawCircle(color = textPrimary, radius = 5.dp.toPx(), center = Offset(ix, iy))
                }
            }
        }

        // High / Low Tide Schedule Chips Row
        if (data.extremes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data.extremes.take(4).forEach { ev ->
                    val chipColor = if (ev.isHighTide) primaryColor else tertiaryColor
                    val chipBg = if (ev.isHighTide) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(chipBg)
                            .border(1.dp, chipColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (ev.isHighTide) "▲ PASANG" else "▼ SURUT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = chipColor
                        )
                        Text(
                            text = ev.timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = textPrimary
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2fm", ev.heightMeters),
                            style = MaterialTheme.typography.labelSmall,
                            color = textSecondary
                        )
                    }
                }
            }
        }

        // Footer hint
        val rateText = String.format(Locale.US, "%+.0f cm/jam", data.rateCmPerHour)
        val flowNote = if (abs(data.rateCmPerHour) >= 12.0) {
            "Arus pasang-surut aktif ($rateText) — waktu ideal ikan mencari makan."
        } else {
            "Arus melandai ($rateText) — geser grafik untuk melihat jam puncak pasang/surut."
        }
        Text(
            text = flowNote,
            style = MaterialTheme.typography.bodySmall,
            color = textSecondary
        )
    }
}

@Composable
private fun TideStatItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor
        )
    }
}
