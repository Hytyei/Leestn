package app.leestn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.leestn.data.Dates
import app.leestn.data.Episode
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors
import java.time.LocalDate

// The shared look: ink-violet surfaces with big rounded corners, pill controls, colour blocks for numbers and a floating dock.

/** A soft rounded surface. */
fun Modifier.card(corner: Dp = 24.dp, color: Color = LColors.Surface): Modifier = this.clip(RoundedCornerShape(corner)).background(color)

/** Page title row: a big title with optional actions on the right. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LColors.Text3)
        }
        trailing?.invoke()
    }
}

/** Row of pill tabs. The selected one is filled with the accent and shows its icon. */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, icons: List<ImageVector?> = emptyList()) {
    Row(modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val bg by animateColorAsState(if (on) LColors.Accent else Color.Transparent, tween(Motion.Quick), label = "tabBg")
            val line by animateColorAsState(if (on) LColors.Accent else LColors.Surface3, tween(Motion.Quick), label = "tabLine")
            val shape = RoundedCornerShape(99.dp)
            Row(
                Modifier.clip(shape).background(bg).border(BorderStroke(1.5.dp, line), shape).pressable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val ic = icons.getOrNull(i)
                if (ic != null) Icon(ic, null, tint = if (on) Color.White else LColors.Text2, modifier = Modifier.size(18.dp))
                Text(l, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else LColors.Text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Last.fm style block: a colour, a small label, a big number and an optional change. Dark text on the bright colour. */
@Composable
fun ColorBlock(label: String, value: String, color: Color, modifier: Modifier = Modifier, change: String? = null) {
    val ink = Color(0xFF14121B)
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(color).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f), maxLines = 1)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 1, lineHeight = 34.sp)
        Text(change ?: " ", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f), maxLines = 1)
    }
}

/** "+74% vs last week" text, or null when there is nothing to compare. */
fun changeText(now: Int, before: Int): String? {
    if (before <= 0) return if (now > 0) "new this week" else null
    val pct = Math.round((now - before) * 100f / before)
    return (if (pct >= 0) "▲ " else "▼ ") + Math.abs(pct) + "% vs last week"
}

object Streaks {
    /** Days in a row up to today (or up to yesterday, if you have not listened yet today). */
    fun current(days: Set<String>, today: LocalDate = LocalDate.now()): Int {
        var d = today
        if (d.toString() !in days) d = d.minusDays(1)
        var n = 0
        while (d.toString() in days) { n++; d = d.minusDays(1) }
        return n
    }

    /** The last 7 days, oldest first: true on days with something listened. */
    fun week(days: Set<String>, today: LocalDate = LocalDate.now()): List<Boolean> = (6 downTo 0).map { today.minusDays(it.toLong()).toString() in days }
}

/** Flame, the streak in days and a segment for each of the last 7 days. */
@Composable
fun StreakCard(streak: Int, week: List<Boolean>, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier.card(28.dp).pressable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = if (streak > 0) Color(0xFFFFA43B) else LColors.Text3, modifier = Modifier.size(40.dp))
        Text("$streak-day streak", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            week.forEachIndexed { i, on ->
                val today = i == week.lastIndex
                Box(
                    Modifier.width(8.dp).height(if (today) 28.dp else 22.dp).clip(RoundedCornerShape(99.dp))
                        .background(if (on) LColors.Accent else LColors.Surface3)
                        .let { if (today) it.border(1.5.dp, LColors.Lilac, RoundedCornerShape(99.dp)) else it },
                )
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = LColors.Text3)
    }
}

/** Wide card for an episode in progress: cover with the time listened and time left in dark pills, then show, episode and a done button. */
@Composable
fun ResumeCard(vm: AppViewModel, ep: Episode, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val p = showOf(ep.pid)
    val secs = vm.prog[ep.id] ?: 0
    val total = ep.seconds
    val frac = if (total > 0) (secs / total.toFloat()).coerceIn(0f, 1f) else 0f
    val left = if (total > secs) (total - secs) / 60 else 0
    val now = vm.playing && vm.playingId == ep.id
    var menu by remember { mutableStateOf(false) }
    Column(modifier.pressable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            Poster(p, Modifier.fillMaxWidth(), ratio = 1.6f, corner = 26.dp)
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
            if (now) Equalizer(true, Modifier.align(Alignment.TopStart).padding(16.dp), color = Color.White, height = 16.dp)
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).pressable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreVert, "More", tint = Color.White)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove from Continue listening") }, onClick = { menu = false; vm.removeFromContinue(ep.id) })
                }
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = 0.62f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(LColors.Accent.copy(alpha = 0.85f)))
                    Text(Dates.clock(secs), Modifier.align(Alignment.CenterStart).padding(start = 12.dp), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                if (left > 0) Text(
                    "$left min left", style = MaterialTheme.typography.labelMedium, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(p.name.ifBlank { "Podcast" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${ep.code} • ${ep.title}", style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).pressable { vm.logListen(ep.id, Dates.today()) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.RadioButtonUnchecked, "Mark as listened", tint = LColors.Text2, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** One tab in the dock. */
class DockTab(val route: String, val label: String, val icon: ImageVector)

/** Floating bottom bar: icons only, the selected one lights up and shows its name. The last tab can show a picture instead of an icon. */
@Composable
fun Dock(tabs: List<DockTab>, selected: String, onSelect: (String) -> Unit, picture: @Composable (String, Boolean) -> Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color(0xFF0D0C12))
            .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val on = t.route == selected
            val tint by animateColorAsState(if (on) LColors.Accent else LColors.Text3, tween(Motion.Quick), label = "dockTint")
            Column(Modifier.weight(1f).pressable { onSelect(t.route) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!picture(t.route, on)) Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(28.dp))
                AnimatedVisibility(on, enter = fadeIn(tween(Motion.Normal)) + expandVertically(tween(Motion.Normal)), exit = fadeOut(tween(Motion.Quick)) + shrinkVertically(tween(Motion.Quick))) {
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** Small round check or empty circle, used for done buttons. */
@Composable
fun DoneDot(done: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(40.dp).clip(CircleShape).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (done) "Listened" else "Mark as listened", tint = if (done) LColors.Accent else LColors.Text2, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun VGap(h: Dp) = Spacer(Modifier.height(h))
