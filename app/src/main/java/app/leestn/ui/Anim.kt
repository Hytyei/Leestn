package app.leestn.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.leestn.ui.theme.LColors

/** Shared timings so every motion in the app feels related. */
object Motion {
    const val Quick = 140
    const val Normal = 220
    const val Slow = 280
    val Ease = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}

/** Clickable that gives a little under the finger, with no bounce on the way back. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.Quick, easing = Motion.Ease), label = "press")
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Fades and rises into place once, with a small delay so lists cascade. Scrolling back to it does not replay it. */
fun Modifier.appear(index: Int = 0, step: Int = 30, maxDelay: Int = 150): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(Motion.Slow, delayMillis = minOf(index * step, maxDelay), easing = Motion.Ease),
        label = "appear",
    )
    this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 8.dp.toPx()
    }
}

/** Soft moving highlight for placeholders while data loads. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    this.background(
        Brush.linearGradient(
            colors = listOf(LColors.Surface2, LColors.Surface3, LColors.Surface2),
            start = androidx.compose.ui.geometry.Offset(x * 600f, 0f),
            end = androidx.compose.ui.geometry.Offset(x * 600f + 400f, 200f),
        ),
    )
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).shimmer())
}

/** Numbers that count up to their value. */
@Composable
fun animatedInt(target: Int, durationMs: Int = 500): Int {
    val v by animateFloatAsState(target.toFloat(), tween(durationMs, easing = Motion.Ease), label = "count")
    return v.toInt()
}
