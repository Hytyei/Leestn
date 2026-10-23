package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.leestn.data.Catalog
import app.leestn.data.Dates
import app.leestn.data.StatsCalc
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors
import java.util.Locale

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val GENRE_COLORS = listOf(LColors.Chart1, LColors.Chart2, LColors.Chart3, LColors.Chart4)

@Composable
internal fun Panel(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
        }
        content()
    }
}

@Composable
fun ProfileScreen(
    vm: AppViewModel,
    openEp: (String) -> Unit,
    openRecap: () -> Unit,
    openSettings: () -> Unit,
    openBadges: () -> Unit,
    openEditProfile: () -> Unit,
) {
    val log = vm.log.toMap()
    val years = remember(log) {
        (log.values.mapNotNull { it.d?.let(Dates::year) } + Dates.today().substring(0, 4)).distinct().sortedDescending()
    }
    var year by rememberSaveable { mutableStateOf(Dates.today().substring(0, 4)) }
    if (year != "all" && year !in years) year = years.first()
    val st = remember(log, year) { StatsCalc.compute(log, year) }
    val recent = log.entries.filter { it.value.d != null && Catalog.episode(it.key) != null }
        .sortedWith(compareByDescending<Map.Entry<String, app.leestn.data.LogEntry>> { it.value.d }.thenByDescending { it.value.h }).take(6)

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = openSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable(onClick = openEditProfile)) { MyAvatar(vm, 76.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(vm.displayName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false))
                            FounderBadge(vm.myRole, size = 24.dp)
                        }
                        FounderTag(vm.myRole)
                        Text(
                            if (vm.connected) "@${vm.serverHandle}" else "Only on this device",
                            style = MaterialTheme.typography.labelMedium, color = LColors.Text3,
                        )
                        Text(
                            vm.profileBio.ifBlank { "Add a short bio in Edit profile." },
                            style = MaterialTheme.typography.bodyMedium, color = if (vm.profileBio.isBlank()) LColors.Text3 else LColors.Text2,
                        )
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionPill("Edit profile", Icons.Rounded.Edit, onClick = openEditProfile)
                    ActionPill("Year recap", Icons.Rounded.AutoAwesome, primary = true, onClick = openRecap)
                }
            }
        }
        item {
            val badges = remember(log) { vm.computeBadges(log) }
            Panel("Badges", "${badges.count { it.earned }} of ${badges.size}") {
                BadgeStrip(badges)
                ActionPill("All badges", Icons.Rounded.AutoAwesome, onClick = openBadges)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(years + "all") { y -> Chip(if (y == "all") "All time" else y, year == y) { year = y } }
            }
        }
        if (st.n == 0) {
            item { EmptyState("Nothing logged ${if (year == "all") "yet" else "in $year"}", "Finish an episode, or log one from its page, and your stats start filling in.") }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CountTile(st.n, "Episodes", Modifier.weight(1f))
                        StatTile(String.format(Locale.US, "%.1f", st.mins / 60.0), "Hours", Modifier.weight(1f))
                        StatTile(if (st.rated > 0) String.format(Locale.US, "%.1f", st.avg) else "–", "Avg rating", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CountTile(st.pods.size, "Shows", Modifier.weight(1f))
                        CountTile(st.sides, "Side episodes", Modifier.weight(1f))
                        CountTile(st.bestStreak, "Day streak", Modifier.weight(1f))
                    }
                }
            }
            item {
                val peak = st.months.indices.maxByOrNull { st.months[it] } ?: 0
                Panel("Hours per month", st.year) {
                    BarChart(st.months.map { it / 60f }, MONTHS.map { it.take(1) }, highlight = peak, format = { String.format(Locale.US, "%.1f h", it) })
                }
            }
            item {
                Panel("Ratings given", "${st.rated} rated") {
                    val peak = st.dist.indices.maxByOrNull { st.dist[it] } ?: 0
                    BarChart(st.dist.map { it.toFloat() }, (1..10).map { it.toString() }, highlight = peak, format = { "${it.toInt()} eps" })
                }
            }
            item {
                Panel("Time by genre") {
                    val top = st.genres.take(4)
                    val other = st.genres.drop(4).sumOf { it.second }
                    val parts = top.mapIndexed { i, g -> g.first to g.second }.let { l -> if (other > 0) l + ("Other" to other) else l }
                    val total = parts.sumOf { it.second }.coerceAtLeast(1)
                    StackBar(parts.mapIndexed { i, g -> g.second.toFloat() to (if (i < 4) GENRE_COLORS[i] else LColors.ChartOther) })
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parts.forEachIndexed { i, g ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(if (i < 4) GENRE_COLORS[i] else LColors.ChartOther))
                                Text(g.first, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${Dates.hours(g.second)} · ${Math.round(g.second * 100f / total)}%", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                            }
                        }
                    }
                }
            }
            item {
                val peak = st.weekdays.indices.maxByOrNull { st.weekdays[it] } ?: 0
                Panel("Busiest weekday", WDAYS[peak]) {
                    BarChart(st.weekdays.map { it / 60f }, WDAYS.map { it.take(1) }, highlight = peak, format = { String.format(Locale.US, "%.1f h", it) })
                }
            }
            item {
                Panel("Listening calendar", st.year) {
                    Heatmap(st.days, st.year.toInt())
                }
            }
            item {
                Panel("Top shows") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        st.pods.take(5).forEachIndexed { rank, x ->
                            val tintX = rememberArtTint(showOf(x.pid).artworkUrl)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${rank + 1}", style = MaterialTheme.typography.headlineSmall, color = if (rank == 0) LColors.Red else LColors.Text3, modifier = Modifier.width(26.dp))
                                Poster(showOf(x.pid), Modifier.width(42.dp), corner = 8.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(showOf(x.pid).name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        vm.showAverage(x.pid)?.let { RatingChip(it.first, Modifier.padding(end = 8.dp)) }
                                        Text(Dates.hours(x.min), style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                                    }
                                    GlowBar(x.min / st.pods.first().min.toFloat(), color = tintX, height = 4.dp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Panel("Highest rated") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        st.top.forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { openEp(r.ep.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Poster(showOf(r.ep.pid), Modifier.width(36.dp), corner = 8.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(r.ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${showOf(r.ep.pid).name} · ${r.ep.code}", style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1)
                                }
                                RatingPill(r.entry.r)
                            }
                        }
                    }
                }
            }
        }
        item {
            Panel("Recent activity") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recent.forEach { (id, l) ->
                        val ep = Catalog.episode(id) ?: return@forEach
                        Row(Modifier.fillMaxWidth().clickable { openEp(id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Poster(showOf(ep.pid), Modifier.width(36.dp), corner = 8.dp)
                            Column(Modifier.weight(1f)) {
                                Text(ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${showOf(ep.pid).name} · ${ep.code} · ${Dates.rel(l.d!!)}", style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1)
                            }
                            RatingPill(l.r)
                        }
                    }
                }
            }
        }
    }
}
