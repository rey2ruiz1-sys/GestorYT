package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.RoseWashSoft
import com.example.ui.theme.StudioWhite

/**
 * Lightweight, hardware-accelerated white and blush-rose studio backdrop
 * using drawWithCache so brushes and paths are cached across recompositions.
 */
@Composable
fun StudioBackground(
    modifier: Modifier = Modifier,
    showTopRightWave: Boolean = true,
    showBottomWave: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioWhite)
            .drawWithCache {
                val w = size.width
                val h = size.height

                val topRightCenter = Offset(w * 0.92f, h * 0.04f)
                val topRightRadius = w * 0.72f
                val topRightGlow = Brush.radialGradient(
                    colors = listOf(
                        RoseWashSoft.copy(alpha = 0.65f),
                        RoseWashSoft.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = topRightCenter,
                    radius = topRightRadius
                )

                val bottomLeftCenter = Offset(w * 0.08f, h * 0.94f)
                val bottomLeftRadius = w * 0.62f
                val bottomLeftGlow = Brush.radialGradient(
                    colors = listOf(
                        RoseWashSoft.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = bottomLeftCenter,
                    radius = bottomLeftRadius
                )

                val topWavePath = if (showTopRightWave) {
                    Path().apply {
                        moveTo(w * 0.56f, 0f)
                        cubicTo(
                            w * 0.70f, h * 0.06f,
                            w * 0.86f, h * 0.04f,
                            w, h * 0.12f
                        )
                        lineTo(w, 0f)
                        close()
                    }
                } else null

                val topWaveBrush = if (showTopRightWave) {
                    Brush.linearGradient(
                        colors = listOf(
                            CrimsonBright.copy(alpha = 0.12f),
                            CrimsonPrimary.copy(alpha = 0.24f)
                        ),
                        start = Offset(w * 0.56f, 0f),
                        end = Offset(w, h * 0.12f)
                    )
                } else null

                val bottomWaveBackPath = if (showBottomWave) {
                    Path().apply {
                        moveTo(0f, h * 0.955f)
                        cubicTo(
                            w * 0.28f, h * 0.995f,
                            w * 0.68f, h * 0.925f,
                            w, h * 0.965f
                        )
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                } else null

                val bottomWaveBackBrush = if (showBottomWave) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            CrimsonPrimary.copy(alpha = 0.22f),
                            RoseWashSoft.copy(alpha = 0.50f),
                            CrimsonBright.copy(alpha = 0.16f)
                        )
                    )
                } else null

                val bottomWaveFrontPath = if (showBottomWave) {
                    Path().apply {
                        moveTo(0f, h * 0.975f)
                        cubicTo(
                            w * 0.34f, h * 1.005f,
                            w * 0.72f, h * 0.95f,
                            w, h * 0.985f
                        )
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                } else null

                val bottomWaveFrontBrush = if (showBottomWave) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            CrimsonPrimary.copy(alpha = 0.32f),
                            CrimsonBright.copy(alpha = 0.18f)
                        )
                    )
                } else null

                onDrawBehind {
                    drawCircle(brush = topRightGlow, radius = topRightRadius, center = topRightCenter)
                    drawCircle(brush = bottomLeftGlow, radius = bottomLeftRadius, center = bottomLeftCenter)

                    if (topWavePath != null && topWaveBrush != null) {
                        drawPath(path = topWavePath, brush = topWaveBrush)
                    }
                    if (bottomWaveBackPath != null && bottomWaveBackBrush != null) {
                        drawPath(path = bottomWaveBackPath, brush = bottomWaveBackBrush)
                    }
                    if (bottomWaveFrontPath != null && bottomWaveFrontBrush != null) {
                        drawPath(path = bottomWaveFrontPath, brush = bottomWaveFrontBrush)
                    }
                }
            },
        content = content
    )
}
