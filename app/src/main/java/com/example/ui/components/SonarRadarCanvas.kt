package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sensors.MarineGeoUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private data class SonarBlip(
    val angleDeg: Float,
    val radiusFraction: Float,
    val sizeFactor: Float,
    val isReefCluster: Boolean
)

@Composable
fun SonarRadarCanvas(
    headingDegrees: Float,
    rangeMeters: Int,
    isScanning: Boolean,
    feedingScore: Int?,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val voidColor = MaterialTheme.colorScheme.background

    val infiniteTransition = rememberInfiniteTransition(label = "sonar_radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isScanning) 1500 else 3600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val blips = remember(feedingScore) {
        val base = listOf(
            SonarBlip(38f, 0.42f, 1.0f, false),
            SonarBlip(122f, 0.66f, 1.3f, true),
            SonarBlip(215f, 0.54f, 0.9f, false),
            SonarBlip(296f, 0.72f, 1.15f, true),
            SonarBlip(64f, 0.78f, 0.85f, false)
        )
        if ((feedingScore ?: 0) >= 65) {
            base + listOf(
                SonarBlip(148f, 0.36f, 1.2f, false),
                SonarBlip(328f, 0.48f, 1.1f, false)
            )
        } else {
            base
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.08f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(surfaceColor, voidColor)
                )
            )
            .border(1.dp, gridColor, RoundedCornerShape(20.dp))
            .semantics {
                contentDescription = "Animasi radar sonar fishfinder jangkauan $rangeMeters meter"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            val diameter = min(size.width, size.height)
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Subtle dark phosphor disc
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.10f),
                        secondaryColor.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Concentric sonar range rings (25%, 50%, 75%, 100%)
            val ringFractions = listOf(0.25f, 0.50f, 0.75f, 1.0f)
            ringFractions.forEachIndexed { idx, fraction ->
                drawCircle(
                    color = if (idx == 3) primaryColor.copy(alpha = 0.45f) else gridColor.copy(alpha = 0.75f),
                    radius = radius * fraction,
                    center = center,
                    style = Stroke(width = if (idx == 3) 2.2f else 1.2f)
                )
            }

            // Crosshair lines (N-S, E-W, and diagonals)
            for (angle in listOf(0, 45, 90, 135)) {
                val rad = angle * PI / 180.0
                val dx = (radius * cos(rad)).toFloat()
                val dy = (radius * sin(rad)).toFloat()
                drawLine(
                    color = gridColor.copy(alpha = if (angle % 90 == 0) 0.7f else 0.35f),
                    start = Offset(center.x - dx, center.y - dy),
                    end = Offset(center.x + dx, center.y + dy),
                    strokeWidth = if (angle % 90 == 0) 1.4f else 1f
                )
            }

            // Outer 10-degree instrument ticks
            for (deg in 0 until 360 step 10) {
                val isMajor = deg % 30 == 0
                val tickLen = if (isMajor) radius * 0.07f else radius * 0.035f
                val rad = (deg - 90) * PI / 180.0
                val outerX = center.x + (radius * cos(rad)).toFloat()
                val outerY = center.y + (radius * sin(rad)).toFloat()
                val innerX = center.x + ((radius - tickLen) * cos(rad)).toFloat()
                val innerY = center.y + ((radius - tickLen) * sin(rad)).toFloat()
                drawLine(
                    color = if (isMajor) secondaryColor.copy(alpha = 0.75f) else gridColor,
                    start = Offset(innerX, innerY),
                    end = Offset(outerX, outerY),
                    strokeWidth = if (isMajor) 2.2f else 1.2f
                )
            }

            // Rotating phosphor radar sweep sector
            rotate(degrees = sweepAngle, pivot = center) {
                val trailSweep = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.76f to Color.Transparent,
                    0.92f to primaryColor.copy(alpha = 0.14f),
                    0.99f to primaryColor.copy(alpha = 0.45f),
                    1.0f to Color.Transparent,
                    center = center
                )
                drawCircle(
                    brush = trailSweep,
                    radius = radius * 0.99f,
                    center = center
                )

                // Leading sonar sweep beam
                drawLine(
                    color = primaryColor,
                    start = center,
                    end = Offset(center.x + radius * 0.99f, center.y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // Scanning pulse ring when AI scan is active
            if (isScanning) {
                val scanProgress = (sweepAngle % 180f) / 180f
                drawCircle(
                    color = secondaryColor.copy(alpha = (1f - scanProgress) * 0.7f),
                    radius = radius * scanProgress,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }

            // Fish echo blips & coral contour arcs that illuminate as sweep passes
            blips.forEach { blip ->
                val diff = ((sweepAngle - blip.angleDeg) % 360f + 360f) % 360f
                val decay = (1f - (diff / 170f)).coerceIn(0.18f, 1f)
                val rad = blip.angleDeg * PI / 180.0
                val bx = center.x + (radius * blip.radiusFraction * cos(rad)).toFloat()
                val by = center.y + (radius * blip.radiusFraction * sin(rad)).toFloat()

                val blipColor = if (blip.isReefCluster) secondaryColor else primaryColor
                // Glow halo
                drawCircle(
                    color = blipColor.copy(alpha = decay * 0.30f),
                    radius = 12f * blip.sizeFactor,
                    center = Offset(bx, by)
                )
                // Fish arch arc
                drawArc(
                    color = blipColor.copy(alpha = decay),
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(bx - 9f * blip.sizeFactor, by - 5f * blip.sizeFactor),
                    size = Size(18f * blip.sizeFactor, 12f * blip.sizeFactor),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
                // Core echo dot
                drawCircle(
                    color = if (blip.isReefCluster) tertiaryColor.copy(alpha = decay) else primaryColor.copy(alpha = decay),
                    radius = 3.2f * blip.sizeFactor,
                    center = Offset(bx, by)
                )
            }

            // Live vessel heading pointer triangle on outer rim
            rotate(degrees = headingDegrees, pivot = center) {
                val tipY = center.y - radius * 0.96f
                val headingPath = Path().apply {
                    moveTo(center.x, tipY)
                    lineTo(center.x - 8f, tipY + 14f)
                    lineTo(center.x + 8f, tipY + 14f)
                    close()
                }
                drawPath(path = headingPath, color = tertiaryColor)
            }

            // Center boat transducer marker
            drawCircle(
                color = primaryColor.copy(alpha = pulseAlpha * 0.35f),
                radius = 11f,
                center = center
            )
            drawCircle(
                color = primaryColor,
                radius = 4.5f,
                center = center
            )
        }

        // Top-left range badge
        Text(
            text = "RNG ${rangeMeters}M",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Top-right live heading badge
        val hdgInt = headingDegrees.roundToInt() % 360
        Text(
            text = "HDG ${hdgInt.toString().padStart(3, '0')}° ${MarineGeoUtils.cardinalDirection(headingDegrees)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Bottom-left frequency telemetry tag
        Text(
            text = if (isScanning) "CHIRP 200kHz • SCANNING" else "CHIRP 200kHz • ACTIVE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )

        // Bottom-right quick score indicator if available
        if (feedingScore != null) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = "SKOR $feedingScore%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
