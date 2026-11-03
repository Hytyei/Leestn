package app.leestn.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.leestn.ui.theme.LColors
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Shared visual pieces: colours taken from artwork and a live equalizer.

/** A vivid colour taken from a cover image, cached so each cover is looked at only once. */
object ArtTint {
    private val cache = HashMap<String, Int>()

    suspend fun of(ctx: Context, url: String): Color? {
        if (url.isBlank()) return null
        cache[url]?.let { return Color(it) }
        val argb: Int = withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(ctx).data(url).size(48).allowHardware(false).build()
                val bmp = (Coil.imageLoader(ctx).execute(req) as? SuccessResult)?.drawable?.toBitmap()
                if (bmp == null) 0 else dominant(bmp)
            } catch (e: Exception) {
                0
            }
        }
        if (argb == 0) return null
        cache[url] = argb
        return Color(argb)
    }

    /** Average of the colourful pixels, nudged so it is always bright enough to read as an accent. */
    private fun dominant(bmp: android.graphics.Bitmap): Int {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return 0
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var wt = 0.0
        for (p in px) {
            android.graphics.Color.colorToHSV(p, hsv)
            val weight = (hsv[1] * hsv[2]).toDouble() + 0.02
            r += android.graphics.Color.red(p) * weight
            g += android.graphics.Color.green(p) * weight
            b += android.graphics.Color.blue(p) * weight
            wt += weight
        }
        if (wt <= 0.0) return 0
        val avg = android.graphics.Color.rgb((r / wt).toInt(), (g / wt).toInt(), (b / wt).toInt())
        android.graphics.Color.colorToHSV(avg, hsv)
        hsv[1] = hsv[1].coerceIn(0.45f, 0.95f)
        hsv[2] = hsv[2].coerceIn(0.55f, 0.9f)
        return android.graphics.Color.HSVToColor(hsv)
    }
}

/** The cover's accent colour, fading smoothly from the previous one. [fallback] is used until it is known. */
@Composable
fun rememberArtTint(url: String, fallback: Color = LColors.Red): Color {
    val ctx = LocalContext.current
    val found by produceState<Color?>(null, url) { value = ArtTint.of(ctx, url) }
    val c by animateColorAsState(found ?: fallback, tween(Motion.Slow), label = "artTint")
    return c
}

/** Little dancing bars that show something is playing. Still when [active] is false. */
@Composable
fun Equalizer(active: Boolean, modifier: Modifier = Modifier, color: Color = LColors.Red, bars: Int = 4, height: Dp = 14.dp) {
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        if (active) {
            val t = rememberInfiniteTransition(label = "eq")
            repeat(bars) { i ->
                val f by t.animateFloat(
                    0.25f, 1f,
                    infiniteRepeatable(tween(380 + i * 140, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "eq$i",
                )
                Box(Modifier.width(3.dp).fillMaxHeight(f).clip(RoundedCornerShape(2.dp)).background(color))
            }
        } else {
            repeat(bars) { Box(Modifier.width(3.dp).fillMaxHeight(0.25f).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.5f))) }
        }
    }
}

/** Thin progress bar in one solid colour. */
@Composable
fun GlowBar(fraction: Float, modifier: Modifier = Modifier, color: Color = LColors.Red, height: Dp = 5.dp, animate: Boolean = true) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), if (animate) tween(Motion.Slow, easing = Motion.Ease) else tween(0), label = "glowBar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color))
    }
}

/** Greeting for the home header. */
fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Still up?"
}
