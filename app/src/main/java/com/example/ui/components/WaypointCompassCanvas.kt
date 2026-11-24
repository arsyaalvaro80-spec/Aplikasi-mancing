package com.example.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

@Composable
fun WaypointCompassCanvas(
    deviceAzimuthDegrees: Float,
    targetBearingDegrees: Float?,
    distanceLabel: String?,
    targetName: String?,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val voidColor = MaterialTheme.colorScheme.background

    val relativeAngle = targetBearingDegrees?.let {
        MarineGeoUtils.relativeBearing(it, deviceAzimuthDegrees)
    }
    val signedTurn = targetBearingDegrees?.let {
        MarineGeoUtils.signedTurnDegrees(it, deviceAzimuthDegrees)
    }
    val isOnCourse = signedTurn != null && abs(signedTurn) <= 12f

    val steeringHint = when {
        signedTurn == null -> "PILIH SPOT TARGET"
        abs(signedTurn) <= 12f -> "HALUAN TEPAT!"
        signedTurn > 0f -> "BELOK KANAN ${signedTurn.roundToInt()}°"
        else -> "BELOK KIRI ${abs(signedTurn).roundToInt()}°"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.18f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(colors = listOf(surfaceColor, voidColor))
            )
            .border(
                width = if (isOnCourse) 2.dp else 1.dp,
                color = if (isOnCourse) primaryColor else gridColor,
                shape = RoundedCornerShape(20.dp)
            )
            .semantics {
                contentDescription = "Kompas penunjuk arah ke spot ${targetName ?: "belum dipilih"}"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val radius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer bezel ring
            drawCircle(
                color = if (isOnCourse) primaryColor.copy(alpha = 0.7f) else gridColor,
                radius = radius,
                center = center,
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = gridColor.copy(alpha = 0.55f),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 1.3f)
            )

            // Fixed lubber line at top (phone bow / forward direction)
            drawLine(
                color = tertiaryColor,
                start = Offset(center.x, center.y - radius - 4f),
                end = Offset(center.x, center.y - radius + 16f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // Rotating compass dial ticks (-deviceAzimuthDegrees so North stays true North)
            rotate(degrees = -deviceAzimuthDegrees, pivot = center) {
                for (deg in 0 until 360 step 15) {
                    val isCardinal = deg % 90 == 0
                    val isNorth = deg == 0
                    val rad = (deg - 90) * PI / 180.0
                    val outer = radius * 0.94f
                    val inner = if (isCardinal) radius * 0.78f else radius * 0.86f

                    val start = Offset(
                        center.x + (inner * cos(rad)).toFloat(),
                        center.y + (inner * sin(rad)).toFloat()
                    )
                    val end = Offset(
                        center.x + (outer * cos(rad)).toFloat(),
                        center.y + (outer * sin(rad)).toFloat()
                    )
                    drawLine(
                        color = when {
                            isNorth -> errorColor
                            isCardinal -> secondaryColor
                            else -> gridColor
                        },
                        start = start,
                        end = end,
                        strokeWidth = if (isNorth) 4.5f else if (isCardinal) 3f else 1.5f,
                        cap = StrokeCap.Round
                    )
                }

                // North triangle indicator on compass ring
                val northPath = Path().apply {
                    moveTo(center.x, center.y - radius * 0.77f)
                    lineTo(center.x - 7f, center.y - radius * 0.66f)
                    lineTo(center.x + 7f, center.y - radius * 0.66f)
                    close()
                }
                drawPath(path = northPath, color = errorColor)
            }

            // Target directional pointer arrow (points straight toward locked waypoint)
            if (relativeAngle != null) {
                rotate(degrees = relativeAngle, pivot = center) {
                    // Glowing shaft
                    drawLine(
                        color = primaryColor.copy(alpha = 0.25f),
                        start = Offset(center.x, center.y + radius * 0.28f),
                        end = Offset(center.x, center.y - radius * 0.66f),
                        strokeWidth = 14f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = primaryColor,
                        start = Offset(center.x, center.y + radius * 0.24f),
                        end = Offset(center.x, center.y - radius * 0.56f),
                        strokeWidth = 4.5f,
                        cap = StrokeCap.Round
                    )
                    // Sharp arrowhead
                    val arrowHead = Path().apply {
                        moveTo(center.x, center.y - radius * 0.72f)
                        lineTo(center.x - 15f, center.y - radius * 0.44f)
                        lineTo(center.x, center.y - radius * 0.52f)
                        lineTo(center.x + 15f, center.y - radius * 0.44f)
                        close()
                    }
                    drawPath(path = arrowHead, color = primaryColor)
                }
            }

            // Center pivot
            drawCircle(
                color = voidColor,
                radius = 12f,
                center = center
            )
            drawCircle(
                color = if (relativeAngle != null) primaryColor else secondaryColor,
                radius = 6f,
                center = center
            )
        }

        // Top-left heading readout
        Text(
            text = "HDG ${(deviceAzimuthDegrees.roundToInt() % 360).toString().padStart(3, '0')}°",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )

        // Top-right target bearing readout
        Text(
            text = targetBearingDegrees?.let {
                "BRG ${(it.roundToInt() % 360).toString().padStart(3, '0')}° ${MarineGeoUtils.cardinalDirection(it)}"
            } ?: "BRG ---°",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // Bottom steering command & distance
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        ) {
            Text(
                text = steeringHint,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isOnCourse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            if (distanceLabel != null) {
                Text(
                    text = "JARAK $distanceLabel",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
