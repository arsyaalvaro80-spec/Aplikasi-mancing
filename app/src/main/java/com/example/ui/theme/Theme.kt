package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MarineDarkColorScheme = darkColorScheme(
    primary = SonarGreen,
    onPrimary = SonarOnGreen,
    primaryContainer = SonarGreenContainer,
    onPrimaryContainer = SonarGreen,
    secondary = OceanCyan,
    onSecondary = OceanOnCyan,
    secondaryContainer = OceanCyanContainer,
    onSecondaryContainer = OceanCyan,
    tertiary = MarineAmber,
    onTertiary = MarineVoid,
    tertiaryContainer = MarineAmberContainer,
    onTertiaryContainer = MarineAmber,
    background = MarineVoid,
    onBackground = MarineTextPrimary,
    surface = MarineHull,
    onSurface = MarineTextPrimary,
    surfaceVariant = MarinePanel,
    onSurfaceVariant = MarineTextSecondary,
    surfaceContainerHighest = MarinePanelElevated,
    outline = MarineGridLine,
    outlineVariant = MarineGridSubtle,
    error = MarineCoralRed,
    onError = MarineVoid,
    errorContainer = MarineCoralRedContainer,
    onErrorContainer = MarineCoralRed
)

private val MarineShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun BintanSonarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MarineDarkColorScheme,
        typography = Typography,
        shapes = MarineShapes,
        content = content
    )
}
