package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import app.leestn.data.Catalog
import app.leestn.data.Dates
import app.leestn.data.Kind
import app.leestn.data.StatsCalc
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors
import java.util.Locale

private val MIX = listOf(262f to 12f, 18f to 330f, 196f to 262f, 330f to 20f, 150f to 200f, 285f to 215f, 28f to 265f, 262f to 18f)
private val MONTHS_L = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val MONTHS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** Story-style year recap built from the real log. Tap right/left halves to move between slides. */
@Composable
fun RecapScreen(vm: AppViewModel, onClose: () -> Unit) {
    val year = remember { Dates.today().substring(0, 4) }
    val st = StatsCalc.compute(vm.log.toMap(), year)
    var i by rememberSaveable { mutableIntStateOf(0) }
    val top = st.pods.firstOrNull()
    val hours = String.format(Locale.US, "%.1f", st.mins / 60.0)
    val persona = when {
        st.n > 0 && st.sides.toFloat() / st.n > 0.12f -> "The Deep Diver" to "You listen to the extras, the commentary and the live shows, not just the main story."
        top != null && StatsCalc.completion(vm.log.toMap(), showOf(top.pid)).let { it.first.toFloat() / it.second > 0.7f } -> "The Completionist" to "You finish what you start. Whole seasons, in order."
        st.avg in 0.1..6.8 -> "The Critic" to "Your ratings are tougher than most. A 9 from you means something."
        else -> "The Explorer" to "You sample widely and follow what grabs you."
    }
    val summary = "My $year on Leestn: ${st.n} episodes, $hours hours" + (top?.let { ", top show ${showOf(it.pid).name}" } ?: "") +
        (if (st.rated > 0) ", average rating ${String.format(Locale.US, "%.1f", st.avg)}/10" else "") + ". I am ${persona.first}."
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable { mutableStateOf(false) }

    val slides: List<@Composable () -> Unit> = if (st.n == 0) listOf({
        Eyebrow("Leestn recap · $year")
        Text("Nothing to recap yet.", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Body("Finish an episode or log one from its page. Your recap builds as you listen.")
    }) else buildList {
        add {
            Eyebrow("Leestn recap · $year")
            Text("Your year in audio, so far.", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Body("1 Jan to ${Dates.short(Dates.today())}. It keeps updating until the year ends.")
        }
        add {
            Eyebrow("Time listened")
            Text(String.format(Locale.US, "%.1f", animatedInt((st.mins / 6.0).toInt(), 1100) / 10.0), fontSize = 96.sp, lineHeight = 96.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("hours across ${st.n} episodes", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Body("About ${Math.round(st.mins / 110.0)} feature films, or ${String.format(Locale.US, "%.1f", st.mins / 1440.0)} days with the volume up. ${st.n - st.sides} main episodes, ${st.sides} side episodes.")
        }
        if (top != null) add {
            val p = showOf(top.pid)
            val (d, t) = StatsCalc.completion(vm.log.toMap(), p)
            Eyebrow("Your top show")
            Poster(p, Modifier.width(150.dp), showName = true)
            Text(p.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Body("${Dates.hours(top.min)} across ${top.n} episodes. You have finished $d of $t main episodes.")
            ThinBar(d / t.toFloat(), color = Color.White)
        }
        add {
            Eyebrow("Your highest rated")
            st.top.take(3).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.entry.r}", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.width(54.dp))
                    Column {
                        Text(r.ep.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("${showOf(r.ep.pid).name} · ${r.ep.code}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        add {
            val peak = st.months.indices.maxByOrNull { st.months[it] } ?: 0
            Eyebrow("Your busiest month")
            Text(MONTHS_L[peak], style = MaterialTheme.typography.displaySmall, color = Color.White)
            BarChart(st.months.map { it / 60f }, MONTHS, highlight = peak, height = 120.dp, format = { String.format(Locale.US, "%.1f h", it) })
        }
        add {
            Eyebrow("Your listener type")
            Text(persona.first, style = MaterialTheme.typography.displaySmall, color = Color.White)
            Body(persona.second)
            Text("${st.sides} side episodes · avg rating ${if (st.rated > 0) String.format(Locale.US, "%.1f", st.avg) else "–"} · best streak ${st.bestStreak} days", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        }
        add {
            Eyebrow("Share it")
            Text("See you at year end.", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Body(summary)
            ActionPill(if (copied) "Copied" else "Copy summary", Icons.Rounded.ContentCopy, primary = true) {
                clipboard.setText(AnnotatedString(summary)); copied = true
            }
        }
    }
    val idx = i.coerceIn(0, slides.size - 1)
    val (a, b) = MIX[idx % MIX.size]
    val ca by animateColorAsState(Color.hsl(a, 0.58f, 0.24f), tween(700), label = "bgA")
    val cb by animateColorAsState(Color.hsl(b, 0.55f, 0.08f), tween(700), label = "bgB")
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ca, cb)))
    ) {
        AnimatedContent(
            targetState = idx,
            transitionSpec = {
                val fwd = targetState > initialState
                (fadeIn(tween(320, delayMillis = 80)) + slideInHorizontally(tween(380)) { if (fwd) it / 6 else -it / 6 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { if (fwd) -it / 6 else it / 6 })
            },
            modifier = Modifier.fillMaxSize(),
            label = "slide",
        ) { n ->
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp).padding(top = 64.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                slides[n.coerceIn(0, slides.size - 1)]()
            }
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 96.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { i = (idx - 1).coerceAtLeast(0) })
            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 96.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { i = (idx + 1).coerceAtMost(slides.size - 1) })
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                slides.indices.forEach { k ->
                    val seg by animateColorAsState(if (k <= idx) Color.White else Color.White.copy(alpha = 0.25f), tween(300), label = "seg")
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(9.dp)).background(seg))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close recap", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun Eyebrow(t: String) = Text(t.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))

@Composable
private fun Body(t: String) = Text(t, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
