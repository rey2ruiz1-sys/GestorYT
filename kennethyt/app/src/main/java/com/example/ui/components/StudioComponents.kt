package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.model.Question
import com.example.ui.theme.CardBorderSoft
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.HairlineDivider
import com.example.ui.theme.InkDark
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.RoseWashSoft
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.StudioWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val formatted = if (number in 1..9) "0$number" else number.toString()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(CrimsonBright, CrimsonDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatted,
            color = Color.White,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = "tnum"
            )
        )
    }
}

@Composable
fun StudioClapperEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = CrimsonPrimary.copy(alpha = 0.28f),
                spotColor = CrimsonPrimary.copy(alpha = 0.28f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(CrimsonBright, CrimsonDark),
                    start = Offset.Zero,
                    end = Offset(120f, 120f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.56f)) {
            val w = this.size.width
            val h = this.size.height

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.08f, h * 0.34f),
                size = Size(w * 0.84f, h * 0.58f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
            )

            val playPath = Path().apply {
                moveTo(w * 0.42f, h * 0.48f)
                lineTo(w * 0.42f, h * 0.78f)
                lineTo(w * 0.66f, h * 0.63f)
                close()
            }
            drawPath(path = playPath, color = CrimsonPrimary)

            val topBar = Path().apply {
                moveTo(w * 0.08f, h * 0.26f)
                lineTo(w * 0.86f, h * 0.08f)
                lineTo(w * 0.90f, h * 0.24f)
                lineTo(w * 0.12f, h * 0.38f)
                close()
            }
            drawPath(path = topBar, color = Color.White)
        }
    }
}

@Composable
fun CircularWhiteButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    tint: Color = InkDark,
    size: Dp = 48.dp,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size),
        shape = CircleShape,
        color = if (enabled) StudioWhite else StudioWhite.copy(alpha = 0.65f),
        shadowElevation = if (enabled) 5.dp else 1.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun YouTubeBadgeButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
            .height(48.dp)
            .width(56.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (enabled) StudioWhite else StudioWhite.copy(alpha = 0.65f),
        shadowElevation = if (enabled) 5.dp else 1.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Box(contentAlignment = Alignment.Center) {
            YouTubePlaySymbol(
                modifier = Modifier
                    .width(28.dp)
                    .height(20.dp),
                alpha = if (enabled) 1f else 0.38f
            )
        }
    }
}

@Composable
fun YouTubePlaySymbol(
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = CrimsonPrimary.copy(alpha = alpha),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(h * 0.28f, h * 0.28f)
        )
        val tri = Path().apply {
            moveTo(w * 0.40f, h * 0.26f)
            lineTo(w * 0.40f, h * 0.74f)
            lineTo(w * 0.68f, h * 0.50f)
            close()
        }
        drawPath(path = tri, color = Color.White.copy(alpha = alpha))
    }
}

@Composable
fun SixDotDragHandle(
    modifier: Modifier = Modifier,
    tint: Color = SlateMuted.copy(alpha = 0.7f)
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val r = size.minDimension * 0.085f
        val col1 = size.width * 0.34f
        val col2 = size.width * 0.66f
        val rows = listOf(0.24f, 0.50f, 0.76f)
        rows.forEach { ry ->
            drawCircle(color = tint, radius = r, center = Offset(col1, size.height * ry))
            drawCircle(color = tint, radius = r, center = Offset(col2, size.height * ry))
        }
    }
}

@Composable
fun QuestionBubbleIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = this.size.minDimension * 0.075f
            val r = this.size.minDimension * 0.42f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(
                color = CrimsonPrimary.copy(alpha = 0.85f),
                radius = r,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "?",
            color = CrimsonPrimary,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun GridHomeIcon(
    modifier: Modifier = Modifier,
    tint: Color = InkDark
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val pad = size.width * 0.12f
        val gap = size.width * 0.14f
        val cell = (size.width - pad * 2f - gap) / 2f
        val stroke = Stroke(width = size.width * 0.09f)
        val radius = CornerRadius(cell * 0.25f, cell * 0.25f)

        val positions = listOf(
            Offset(pad, pad),
            Offset(pad + cell + gap, pad),
            Offset(pad, pad + cell + gap),
            Offset(pad + cell + gap, pad + cell + gap)
        )
        positions.forEach { pos ->
            drawRoundRect(
                color = tint,
                topLeft = pos,
                size = Size(cell, cell),
                cornerRadius = radius,
                style = stroke
            )
        }
    }
}

@Composable
fun QuestionCardItem(
    number: Int,
    question: Question,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("question_card_${question.id}"),
        shape = RoundedCornerShape(20.dp),
        color = StudioWhite,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, CardBorderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val wave = Path().apply {
                        moveTo(w * 0.62f, h)
                        cubicTo(w * 0.78f, h * 0.88f, w * 0.90f, h * 0.56f, w, h * 0.40f)
                        lineTo(w, h)
                        close()
                    }
                    val waveColor = RoseWashSoft.copy(alpha = 0.55f)
                    onDrawBehind {
                        drawPath(path = wave, color = waveColor)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberBadge(number = number, size = 40.dp)

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(HairlineDivider)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = question.text,
                modifier = Modifier.weight(1f),
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                color = InkDark
            )

            Spacer(modifier = Modifier.width(12.dp))

            QuestionBubbleIndicator(size = 32.dp)
        }
    }
}

/**
 * Lock/unlock control for accidental-touch protection in Viewer mode.
 * - Unlocked: single tap activates lock immediately.
 * - Locked: holding ~900 ms fills the circular progress ring and unlocks cleanly.
 */
@Composable
fun ViewerLockButton(
    isLocked: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onShortTapWhileLocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val cdText = if (isLocked) {
        stringResource(R.string.cd_unlock_viewer)
    } else {
        stringResource(R.string.cd_lock_viewer)
    }

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .semantics {
                contentDescription = cdText
                role = Role.Button
            }
            .pointerInput(isLocked) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!isLocked) {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            onLock()
                        }
                    } else {
                        isHolding = true
                        var unlocked = false
                        val animJob: Job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                            )
                        }
                        val timerJob: Job = scope.launch {
                            delay(900L)
                            unlocked = true
                            isHolding = false
                            progress.snapTo(0f)
                            onUnlock()
                        }
                        val up = waitForUpOrCancellation()
                        animJob.cancel()
                        timerJob.cancel()
                        isHolding = false
                        scope.launch { progress.snapTo(0f) }
                        if (!unlocked) {
                            val heldMs = (up?.uptimeMillis ?: 0L) - down.uptimeMillis
                            if (up != null && heldMs >= 880L) {
                                onUnlock()
                            } else {
                                onShortTapWhileLocked()
                            }
                        }
                    }
                }
            },
        shape = CircleShape,
        color = if (isLocked) CrimsonPrimary else StudioWhite,
        shadowElevation = 5.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isLocked) CrimsonDark else CardBorderSoft
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLocked && (isHolding || progress.value > 0f)) {
                Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Icon(
                imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = if (isLocked) Color.White else InkDark,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

/**
 * Applies Street Mode screen-on and temporary high brightness ONLY while the screen is
 * active in foreground (RESUMED). Automatically restores normal device behavior on
 * backgrounding, screen lock, disabling Street Mode, or leaving the session screen.
 */
@Composable
fun StreetModeWindowEffect(
    keepScreenOn: Boolean,
    boostBrightness: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner, keepScreenOn, boostBrightness) {
        val window = context.findActivity()?.window
        val initialBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        fun applyForegroundState() {
            if (window == null) return
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val attrs = window.attributes
                attrs.screenBrightness = if (boostBrightness) {
                    STREET_MODE_BRIGHTNESS
                } else {
                    initialBrightness
                }
                window.attributes = attrs
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val attrs = window.attributes
                attrs.screenBrightness = initialBrightness
                window.attributes = attrs
            }
        }

        fun restoreNormalState() {
            if (window == null) return
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val attrs = window.attributes
            attrs.screenBrightness = initialBrightness
            window.attributes = attrs
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            applyForegroundState()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> applyForegroundState()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> restoreNormalState()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            restoreNormalState()
        }
    }
}

fun performSubtleHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

private const val STREET_MODE_BRIGHTNESS = 0.96f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
