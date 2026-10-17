package app.leestn.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.leestn.data.Dates
import app.leestn.ui.theme.LColors
import java.time.LocalDate
import java.util.Locale

private fun niceMax(m: Float): Float {
    if (m <= 0f) return 1f
    val e = Math.pow(10.0, Math.floor(Math.log10(m.toDouble()))).toFloat()
    val f = m / e
    return (if (f <= 1f) 1f else if (f <= 2f) 2f else if (f <= 5f) 5f else 10f) * e
}

private fun tick(v: Float): String = if (v == Math.floor(v.toDouble()).toFloat()) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

/** Single-series column chart. One accent colour, recessive grid, the peak bar labelled directly. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    height: Dp = 140.dp,
    format: (Float) -> String = { tick(it) },
) {
    val max = niceMax(values.maxOrNull() ?: 1f)
    val labelH = 18.dp
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Text(tick(max), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = LColors.Text3)
            Text(tick(max / 2), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = LColors.Text3)
            Text("0", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = LColors.Text3)
        }
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().height(height).drawBehind {
                    val c = LColors.Line
                    drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                    drawLine(c, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
                    drawLine(LColors.Surface3, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                values.forEachIndexed { i, v ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (highlight == i && v > 0f) {
                            Text(format(v), fontSize = 10.sp, color = LColors.Text, maxLines = 1)
                        }
                        if (v > 0f) {
                            val grow by animateGrow(index = i)
                            val barH = (height - labelH) * (v / max).coerceIn(0f, 1f) * grow
                            Box(
                                Modifier.fillMaxWidth().height(barH)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (highlight == i) LColors.Red else LColors.Red.copy(alpha = 0.7f))
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                labels.forEach {
                    Text(it, modifier = Modifier.weight(1f), fontSize = 10.sp, color = LColors.Text3, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/** Horizontal stacked bar with a 2dp gap between segments. */
@Composable
fun StackBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.forEach { (w, c) -> Box(Modifier.weight(w.coerceAtLeast(0.01f)).fillMaxHeight().background(c)) }
    }
}

/** 0 to 1 once the chart is on screen, staggered by index. */
@Composable
internal fun animateGrow(index: Int = 0): State<Float> {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    return animateFloatAsState(if (go) 1f else 0f, tween(650, delayMillis = minOf(index * 35, 400), easing = Motion.Ease), label = "grow")
}

@Composable
fun ThinBar(fraction: Float, modifier: Modifier = Modifier, color: Color = LColors.Red) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(Motion.Slow, easing = Motion.Ease), label = "bar")
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(LColors.Surface3)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color))
    }
}

/** Calendar heatmap: one column per week, one row per weekday (Monday first). */
@Composable
fun Heatmap(days: Map<String, Int>, year: Int, modifier: Modifier = Modifier) {
    val first = LocalDate.of(year, 1, 1)
    val pad = first.dayOfWeek.value - 1
    val total = first.lengthOfYear()
    val cols = (pad + total + 6) / 7
    val cell = 11.dp
    val gap = 3.dp
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = LColors.Text3)
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    Row(modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width((cell + gap) * cols).height((cell + gap) * 7 + 16.dp)) {
            val step = (cell + gap).toPx()
            val c = cell.toPx()
            for (m in 0 until 12) {
                val di = LocalDate.of(year, m + 1, 1).dayOfYear - 1
                drawText(tm, months[m], topLeft = Offset(((pad + di) / 7) * step, 0f), style = labelStyle)
            }
            for (i in 0 until total) {
                val d = first.plusDays(i.toLong()).toString()
                val mins = days[d] ?: 0
                val color = when {
                    mins == 0 -> LColors.Surface3
                    mins < 30 -> LColors.Red.copy(alpha = 0.28f)
                    mins < 60 -> LColors.Red.copy(alpha = 0.5f)
                    mins < 100 -> LColors.Red.copy(alpha = 0.75f)
                    else -> LColors.Red
                }
                val faded = d > Dates.today()
                val x = ((pad + i) / 7) * step
                val y = ((pad + i) % 7) * step + 16.dp.toPx()
                drawRoundRect(if (faded) color.copy(alpha = 0.3f) else color, Offset(x, y), Size(c, c), CornerRadius(3.dp.toPx()))
            }
        }
    }
}
