package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KennethLightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = RosePillBackground,
    onPrimaryContainer = CrimsonDark,
    secondary = CrimsonDark,
    onSecondary = Color.White,
    secondaryContainer = BlushWashLight,
    onSecondaryContainer = InkDark,
    tertiary = RoseWaveAccent,
    onTertiary = InkDark,
    background = StudioWhite,
    onBackground = InkDark,
    surface = StudioWhite,
    onSurface = InkDark,
    surfaceVariant = BlushBackground,
    onSurfaceVariant = SlateMuted,
    outline = HairlineDivider,
    outlineVariant = CardBorderSoft,
    error = ErrorRose,
    onError = Color.White
)

val KennethShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun KennethYTTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KennethLightColorScheme,
        typography = KennethTypography,
        shapes = KennethShapes,
        content = content
    )
}
