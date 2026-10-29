package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.leestn.data.Episode
import app.leestn.data.Podcast
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors
import java.util.Locale

/** Series Graph style scale: deep green for the best, through yellow and orange, to red. */
fun ratingColor(r: Double): Color = when {
    r >= 9.0 -> Color(0xFF1E9E57)
    r >= 8.0 -> Color(0xFF55B85A)
    r >= 7.0 -> Color(0xFF9CCB3B)
    r >= 6.0 -> Color(0xFFE6C12F)
    r >= 5.0 -> Color(0xFFF0912A)
    else -> Color(0xFFD94B45)
}

fun fmtRating(r: Double): String = String.format(Locale.US, "%.1f", r)

/** Small coloured pill like "8.2" that reads at a glance. */
@Composable
fun RatingChip(r: Double, modifier: Modifier = Modifier) {
    Text(
        fmtRating(r),
        modifier = modifier.clip(RoundedCornerShape(7.dp)).background(ratingColor(r)).padding(horizontal = 7.dp, vertical = 2.dp),
        color = Color(0xFF101010), fontWeight = FontWeight.Bold, fontSize = 12.sp,
    )
}

private class Col(val label: String, val eps: List<Episode>)

/** Main episodes grouped by season. Long seasons are cut into blocks of 10, side by side, so the grid stays short and scrolls sideways. */
private fun columns(p: Podcast): List<Col> {
    val out = ArrayList<Col>()
    for (c in p.cats) {
        val mains = p.mains.filter { it.cat.id == c.id }
        if (mains.isEmpty()) continue
        val base = c.short.ifBlank { "" }
        if (mains.size <= 10) {
            out.add(Col(base.ifBlank { "All" }, mains))
        } else {
            mains.chunked(10).forEachIndexed { i, part ->
                val from = i * 10 + 1
                out.add(Col((if (base.isBlank()) "" else "$base ") + "$from-${from + part.size - 1}", part))
            }
        }
    }
    return out
}

@Composable
private fun Cell(value: Double?, shownText: String, onClick: (() -> Unit)?) {
    val base = Modifier.size(width = 38.dp, height = 30.dp).clip(RoundedCornerShape(9.dp))
    if (value == null) {
        Box(base.background(LColors.Surface2), contentAlignment = Alignment.Center) { Text("", fontSize = 11.sp) }
    } else {
        Box(
            base.background(ratingColor(value)).let { if (onClick != null) it.clickable(onClick = onClick) else it },
            contentAlignment = Alignment.Center,
        ) { Text(shownText, color = Color(0xFF101010), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

/**
 * Every main episode as a coloured square, one column per season, like Series Graph.
 * [group] shows the average of everyone on your server instead of your own ratings.
 */
@Composable
fun RatingGraph(vm: AppViewModel, p: Podcast, group: Boolean, openEp: (String) -> Unit) {
    val cols = remember(p.id, p.eps.size) { columns(p) }
    if (cols.isEmpty()) return
    val rows = cols.maxOf { it.eps.size }
    val g = vm.group[p.id]
    fun valueOf(ep: Episode): Double? =
        if (!vm.counts(ep)) null else if (group) g?.episodes?.get(ep.id)?.first else vm.log[ep.id]?.r?.toDouble()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.height(18.dp))
            for (r in 1..rows) Box(Modifier.width(26.dp).height(30.dp), contentAlignment = Alignment.CenterEnd) {
                Text("$r", color = LColors.Text3, fontSize = 10.sp, modifier = Modifier.padding(end = 4.dp))
            }
            Box(Modifier.height(30.dp).padding(top = 6.dp), contentAlignment = Alignment.CenterEnd) { Text("avg", color = LColors.Text3, fontSize = 10.sp, modifier = Modifier.padding(end = 4.dp)) }
        }
        cols.forEach { col ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) { Text(col.label, color = LColors.Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                for (r in 0 until rows) {
                    val ep = col.eps.getOrNull(r)
                    if (ep == null) {
                        Box(Modifier.size(width = 38.dp, height = 30.dp))
                    } else {
                        val v = valueOf(ep)
                        Cell(v, if (v == null) "" else if (group) fmtRating(v) else v.toInt().toString()) { openEp(ep.id) }
                    }
                }
                val vals = col.eps.mapNotNull { valueOf(it) }
                Box(Modifier.padding(top = 6.dp)) {
                    if (vals.isEmpty()) Cell(null, "", null) else Cell(vals.average(), fmtRating(vals.average()), null)
                }
            }
        }
    }
}

@Composable
private fun StatBox(title: String, value: Double?, sub: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(LColors.Surface2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = LColors.Text3, maxLines = 1)
        if (value == null) Text("–", style = MaterialTheme.typography.headlineSmall, color = LColors.Text3)
        else Text(fmtRating(value), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = ratingColor(value))
        Text(sub, style = MaterialTheme.typography.labelSmall, color = LColors.Text3, maxLines = 1)
    }
}

/** The ratings block on a show page: averages, your rating of the whole show, and the episode graph. */
@Composable
fun ShowRatingsPanel(vm: AppViewModel, p: Podcast, openEp: (String) -> Unit) {
    LaunchedEffect(p.id, vm.connected) { vm.loadGroupRatings(p.id) }
    var mode by rememberSaveable(p.id) { mutableStateOf("mine") }
    val mine = vm.showAverage(p.id)
    val own = vm.showRatings[p.id]
    val g = vm.group[p.id]
    val groupValue = g?.showAvg ?: g?.epsAvg
    val groupN = if (g?.showAvg != null) g.showN else g?.epsN ?: 0

    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(24.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ratings", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorBlock("Episodes avg", mine?.let { fmtRating(it.first) } ?: "–", LColors.Lilac, Modifier.weight(1f), if (mine == null) "rate some episodes" else "${mine.second} rated")
            ColorBlock("Your rating", own?.toString() ?: "–", LColors.Mint, Modifier.weight(1f), if (own == null) "not rated" else "of the show")
            if (vm.connected) ColorBlock("Server avg", groupValue?.let { fmtRating(it) } ?: "–", LColors.Sky, Modifier.weight(1f), if (groupValue == null) "no ratings yet" else "$groupN rating${if (groupN == 1) "" else "s"}")
        }
        Text("Rate the whole show", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..10) {
                val on = own != null && n <= own
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) ratingColor(own!!.toDouble()) else LColors.Surface3).pressable { vm.rateShow(p.id, n) },
                    contentAlignment = Alignment.Center,
                ) { Text("$n", color = if (on) Color(0xFF101010) else LColors.Text3, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (p.loaded && p.mains.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Episode ratings", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (vm.connected) {
                    Chip("Mine", mode == "mine") { mode = "mine" }
                    Chip("Server", mode == "group") { mode = "group" }
                }
            }
            RatingGraph(vm, p, group = vm.connected && mode == "group", openEp = openEp)
            Text(
                if (mode == "group" && vm.connected) "Average of everyone on your server. Tap a square to open the episode."
                else "Your ratings by season. Tap a square to open the episode and rate it.",
                style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
            )
        }
    }
}
