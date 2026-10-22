package app.leestn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.leestn.data.Dates
import app.leestn.data.Podcast
import app.leestn.state.AppViewModel
import app.leestn.state.NowMeta
import app.leestn.ui.theme.LColors
import kotlinx.coroutines.delay

/** A show for artwork purposes, falling back to the artwork saved with the now-playing record. */
fun nowPoster(m: NowMeta): Podcast {
    val p = showOf(m.pid)
    return if (p.artworkUrl.isBlank() && m.art.isNotBlank()) Podcast(m.pid, m.show.ifBlank { p.name }, "", "", "", artworkUrl = m.art) else p
}

private fun speedLabel(s: Float) = (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()) + "×"

/** The compact player bar that sits above the bottom navigation on every screen. */
@Composable
fun MiniPlayer(vm: AppViewModel, meta: NowMeta, onOpen: () -> Unit) {
    val total = if (vm.durationSec > 0) vm.durationSec else meta.dur
    val frac by animateFloatAsState(if (total > 0) (vm.t / total).coerceIn(0f, 1f) else 0f, tween(250, easing = LinearEasing), label = "mini")
    val poster = nowPoster(meta)
    val tint = rememberArtTint(poster.artworkUrl)
    val btn = lerp(tint, Color.Black, 0.25f)
    val artScale by animateFloatAsState(if (vm.playing) 1f else 0.9f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "miniArt")
    Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.30f), LColors.Surface))).clickable(onClick = onOpen)) {
        GlowBar(frac, color = tint, height = 2.dp, animate = false)
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Poster(poster, Modifier.width(40.dp).graphicsLayer { scaleX = artScale; scaleY = artScale }, corner = 8.dp)
            Column(Modifier.weight(1f)) {
                Text(meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (vm.playerError != null) "Playback problem. Tap to open." else if (vm.buffering) "Loading…" else "${meta.show} · ${Dates.clock(vm.t.toInt())}",
                    style = MaterialTheme.typography.bodySmall, color = if (vm.playerError != null) LColors.Red else LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Equalizer(vm.playing, color = tint, height = 16.dp)
            Text("−15", modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { vm.skip(-15) }.padding(8.dp), style = MaterialTheme.typography.labelMedium, color = LColors.Text2)
            PlayButton(vm.playing, Modifier.pulseWhen(vm.playing, tint), size = 42.dp, filled = true, container = btn) { vm.togglePlay() }
            Text("+30", modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { vm.skip(30) }.padding(8.dp), style = MaterialTheme.typography.labelMedium, color = LColors.Text2)
        }
    }
}

@Composable
private fun RoundText(label: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(LColors.Surface3).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Full player: big artwork, seeking, speed, sleep timer and links to the episode and show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(vm: AppViewModel, meta: NowMeta, onClose: () -> Unit, openEpisode: (String) -> Unit, openShow: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val total = if (vm.durationSec > 0) vm.durationSec else meta.dur
    var drag by remember { mutableStateOf<Float?>(null) }
    var sleepOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    if (speedOpen) SpeedDialog(vm) { speedOpen = false }
    // remaining sleep time, refreshed once a second only while the timer runs
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.sleepEndsAt) {
        while (vm.sleepEndsAt > 0L) { now = System.currentTimeMillis(); delay(1000) }
    }
    val sleepLeft = if (vm.sleepEndsAt > 0L) ((vm.sleepEndsAt - now) / 1000).coerceAtLeast(0) else 0L

    val poster = nowPoster(meta)
    val tint = rememberArtTint(poster.artworkUrl)
    val btn = lerp(tint, Color.Black, 0.25f)
    // the cover swells while playing and settles back when paused
    val artScale by animateFloatAsState(if (vm.playing) 1f else 0.86f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "art")
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = LColors.Surface) {
      Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.5f), LColors.Surface)))) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.7f).graphicsLayer { scaleX = artScale; scaleY = artScale }.glow(tint, 36.dp, 0.55f)) {
                Poster(poster, Modifier.fillMaxWidth(), corner = 24.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meta.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Equalizer(vm.playing, color = tint, height = 12.dp)
                    Text(meta.show, style = MaterialTheme.typography.bodyMedium, color = LColors.Text2, textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = (drag ?: vm.t).coerceIn(0f, maxOf(total, 1).toFloat()),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { vm.seekTo(it) }; drag = null },
                    valueRange = 0f..maxOf(total, 1).toFloat(),
                    enabled = total > 0,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = tint, inactiveTrackColor = Color.White.copy(alpha = 0.14f)),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(Dates.clock((drag ?: vm.t).toInt()), style = MaterialTheme.typography.labelSmall, color = LColors.Text3, modifier = Modifier.weight(1f))
                    Text(if (total > 0) "−" + Dates.clock((total - (drag ?: vm.t)).toInt().coerceAtLeast(0)) else "–", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundText(speedText(vm.speed)) { speedOpen = true }
                RoundText("−15") { vm.skip(-15) }
                PlayButton(vm.playing, Modifier.pulseWhen(vm.playing, tint), size = 72.dp, filled = true, container = btn) { vm.togglePlay() }
                RoundText("+30") { vm.skip(30) }
                Box(Modifier.size(52.dp).clip(CircleShape).background(if (vm.sleepEndsAt > 0L) LColors.RedSoft else LColors.Surface3).pressable { sleepOpen = !sleepOpen }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (vm.sleepEndsAt > 0L) LColors.Red else Color.White)
                }
            }
            AnimatedVisibility(sleepOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (sleepLeft > 0) "Pausing in ${Dates.clock(sleepLeft.toInt())}" else "Pause playback after",
                        style = MaterialTheme.typography.labelMedium, color = LColors.Text3,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 h").forEach { (m, label) ->
                            Chip(label, (m == 0 && vm.sleepEndsAt == 0L)) { vm.setSleep(m); if (m == 0) sleepOpen = false }
                        }
                    }
                }
            }
            if (vm.playerError != null) Text("Playback problem: ${vm.playerError}", color = LColors.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.animateContentSize()) {
                ActionPill("Episode and transcript", Icons.Rounded.Subtitles) { onClose(); openEpisode(meta.id) }
                ActionPill("Show", Icons.Rounded.Podcasts) { onClose(); openShow(meta.pid) }
            }
        }
      }
    }
}
