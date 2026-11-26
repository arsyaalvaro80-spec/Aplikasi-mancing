package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.marine.DayTideAndSolunar
import com.example.data.marine.TidalRegime
import com.example.viewmodel.TideUiState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun SolunarCalendarCard(
    tideState: TideUiState,
    modifier: Modifier = Modifier
) {
    val tideData = (tideState as? TideUiState.Ready)?.data ?: return
    val days = tideData.sevenDayCalendar
    if (days.isEmpty()) return

    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val selectedDay = days.getOrElse(selectedDayIndex) { days.first() }
    val lunar = selectedDay.lunarInfo

    val regimeColor = tidalRegimeColor(lunar.tidalRegime)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("solunar_calendar_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = "Kalender Solunar & Pasang 7 Hari",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "KALENDER SOLUNAR & PASANG 7 HARI",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ketuk hari di bawah untuk cek jadwal pasang & jam gacor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 7-Day Selector Strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(days, key = { _, item -> item.dayIndex }) { index, day ->
                    val isSelected = index == selectedDayIndex
                    val dayRegimeColor = tidalRegimeColor(day.lunarInfo.tidalRegime)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .width(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedDayIndex = index }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("solunar_day_${day.dayIndex}")
                    ) {
                        Text(
                            text = day.shortDayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        MoonPhaseDisc(
                            phaseAngleRad = day.lunarInfo.phaseAngleRad,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${day.lunarInfo.illuminationPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "▲ ${String.format(Locale.US, "%+.1fm", day.maxHighMeters)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = when (day.lunarInfo.tidalRegime) {
                                TidalRegime.SPRING_TIDE -> "P. Besar"
                                TidalRegime.MODERATE_TIDE -> "Sedang"
                                TidalRegime.NEAP_TIDE -> "Perbani"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = dayRegimeColor,
                            maxLines = 1
                        )
                    }
                }
            }

            // Selected Day Full Solunar & Tide Detail Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, regimeColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        MoonPhaseDisc(
                            phaseAngleRad = lunar.phaseAngleRad,
                            modifier = Modifier.size(34.dp)
                        )
                        Column {
                            Text(
                                text = "${selectedDay.dateLabel} • ${lunar.phaseName}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Cahaya Bulan ${lunar.illuminationPercent}% • ${lunar.tidalRegime.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = regimeColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${lunar.biteScore}% ${lunar.biteLabel}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = regimeColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(regimeColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Tide High / Low / Wave Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SolunarMetricBox(
                        title = "PASANG PUNCAK",
                        value = selectedDay.peakHighSummary,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    SolunarMetricBox(
                        title = "SURUT TERENDAH",
                        value = selectedDay.peakLowSummary,
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SolunarMetricBox(
                        title = "JAM GACOR UTAMA (MAJOR)",
                        value = lunar.majorWindows.joinToString("  •  "),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    SolunarMetricBox(
                        title = "TAMBAHAN (MINOR) • OMBAK",
                        value = "${lunar.minorWindows.joinToString(", ")} • ${String.format(Locale.US, "%.1fm", selectedDay.maxWaveMeters)}",
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Waves,
                        contentDescription = null,
                        tint = regimeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = lunar.tidalRegime.advice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SolunarMetricBox(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MoonPhaseDisc(
    phaseAngleRad: Double,
    modifier: Modifier = Modifier
) {
    val litColor = Color(0xFFFFD670)
    val darkColor = Color(0xFF1A283A)
    val rimColor = Color(0xFF567396)

    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f - 1.5f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Base dark sphere
        drawCircle(color = darkColor, radius = r, center = Offset(cx, cy))

        // Draw illuminated portion accurately using cosine limb projection
        val cosAngle = cos(phaseAngleRad).toFloat()
        val isWaxing = sin(phaseAngleRad) >= 0.0

        val path = Path()
        val steps = 32
        // Outer limb semicircle (right side if waxing, left side if waning)
        for (i in 0..steps) {
            val theta = -PI / 2.0 + (PI * i / steps)
            val y = cy + (r * sin(theta)).toFloat()
            val limbX = if (isWaxing) {
                cx + (r * cos(theta)).toFloat()
            } else {
                cx - (r * cos(theta)).toFloat()
            }
            if (i == 0) path.moveTo(limbX, y) else path.lineTo(limbX, y)
        }
        // Terminator ellipse curve back
        for (i in steps downTo 0) {
            val theta = -PI / 2.0 + (PI * i / steps)
            val y = cy + (r * sin(theta)).toFloat()
            val terminatorOffset = (r * cos(theta)).toFloat() * cosAngle
            val tx = if (isWaxing) {
                cx + terminatorOffset
            } else {
                cx - terminatorOffset
            }
            path.lineTo(tx, y)
        }
        path.close()

        // Only draw lit half when illumination > 3%
        if (((1.0 - cos(phaseAngleRad)) * 0.5) > 0.03) {
            drawPath(path = path, color = litColor)
        }

        drawCircle(
            color = rimColor,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
    }
}

@Composable
private fun tidalRegimeColor(regime: TidalRegime): Color = when (regime) {
    TidalRegime.SPRING_TIDE -> MaterialTheme.colorScheme.primary
    TidalRegime.MODERATE_TIDE -> MaterialTheme.colorScheme.secondary
    TidalRegime.NEAP_TIDE -> MaterialTheme.colorScheme.tertiary
}
