package app.leestn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.leestn.data.Catalog
import app.leestn.data.Dates
import app.leestn.data.Episode
import app.leestn.data.Podcast
import app.leestn.state.AppViewModel
import app.leestn.state.Load
import app.leestn.ui.theme.LColors

@Composable
fun PosterCard(p: Podcast, onClick: () -> Unit, rating: Double? = null, modifier: Modifier = Modifier.width(124.dp)) {
    Column(modifier.pressable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Poster(p, Modifier.fillMaxWidth(), showName = true)
            if (rating != null) RatingChip(rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(p.name.ifBlank { "Podcast" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(p.genre.ifBlank { p.host }.uppercase(), style = MaterialTheme.typography.labelSmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A recent episode from a subscription. */
@Composable
fun EpisodeCard(vm: AppViewModel, ep: Episode, onClick: () -> Unit) {
    val p = showOf(ep.pid)
    Column(Modifier.width(140.dp).pressable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Poster(p, Modifier.fillMaxWidth())
            Text(
                Dates.rel(ep.date),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White, style = MaterialTheme.typography.labelSmall,
            )
            if (vm.playing && vm.playingId == ep.id) Equalizer(true, Modifier.align(Alignment.TopEnd).padding(10.dp), color = Color.White, height = 14.dp)
            PlayButton(vm.playing && vm.playingId == ep.id, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), size = 34.dp, filled = true, onClick = { vm.play(ep.id) })
        }
        Text(ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(p.name, style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun UpNextCard(vm: AppViewModel, ep: Episode, sideCount: Int, onClick: () -> Unit) {
    val p = showOf(ep.pid)
    Column(Modifier.width(140.dp).pressable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Poster(p, Modifier.fillMaxWidth())
            Text(
                ep.code,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(LColors.Red).padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
            )
            PlayButton(vm.playing && vm.playingId == ep.id, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), size = 34.dp, filled = true, onClick = { vm.play(ep.id) })
        }
        Text(ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (sideCount > 0) "+$sideCount side first" else p.name,
            style = MaterialTheme.typography.bodySmall, color = if (sideCount > 0) LColors.Side else LColors.Text3, maxLines = 1,
        )
    }
}

/** Spotify-style quick pick: a cover and a name in one rounded tile. */
@Composable
private fun QuickTile(vm: AppViewModel, p: Podcast, modifier: Modifier, onClick: () -> Unit) {
    val tint = rememberArtTint(p.artworkUrl)
    val live = vm.playing && Catalog.episode(vm.playingId ?: "")?.pid == p.id
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.30f), LColors.Surface))).pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Poster(p, Modifier.width(56.dp), corner = 0.dp)
        Text(p.name.ifBlank { "Podcast" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (live) Equalizer(true, Modifier.padding(end = 10.dp), color = tint, height = 14.dp)
    }
}

/** Last.fm-style ranked list: big rank number, cover and a bar for how much you listened. */
@Composable
private fun TopShowRow(rank: Int, p: Podcast, count: Int, max: Int, onClick: () -> Unit) {
    val tint = rememberArtTint(p.artworkUrl)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${rank + 1}", style = MaterialTheme.typography.displaySmall, color = if (rank == 0) LColors.Accent else LColors.Text3.copy(alpha = 0.7f), modifier = Modifier.width(34.dp))
        Poster(p, Modifier.width(52.dp), corner = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name.ifBlank { "Podcast" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("$count ep${if (count == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
            }
            GlowBar(count / max.coerceAtLeast(1).toFloat(), color = tint, height = 5.dp)
        }
    }
}

private fun daysAgo(d: String, today: LocalDate): Long? = try { ChronoUnit.DAYS.between(LocalDate.parse(d), today) } catch (e: Exception) { null }

@Composable
fun HomeScreen(
    vm: AppViewModel,
    openShow: (String) -> Unit,
    openEp: (String) -> Unit,
    openItem: (app.leestn.data.RFeedItem) -> Unit,
    openPerson: (String) -> Unit,
    goFriends: () -> Unit,
    goDiscover: () -> Unit,
    openSettings: () -> Unit = {},
    goProfile: () -> Unit = {},
) {
    LaunchedEffect(Unit) { vm.loadTrending(); vm.loadForYou() }
    LaunchedEffect(vm.connected) { if (vm.connected) vm.loadFeed() }
    val picks = run {
        val lists = vm.forYou.values.toList()
        (0 until 4).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }.distinctBy { it.id }.take(12)
    }

    val continuing = vm.prog.keys.toList().mapNotNull { id -> Catalog.episode(id) }
    val fresh = vm.subs.flatMap { pid -> Catalog.show(pid)?.eps?.take(6) ?: emptyList() }
        .filter { vm.log[it.id]?.d == null && it.audioUrl.isNotBlank() }
        .sortedByDescending { it.date }
        .take(10)
    val shows = vm.log.keys.mapNotNull { Catalog.episode(it)?.pid }.distinct().mapNotNull { Catalog.show(it) }
    val ups = shows.mapNotNull { p ->
        val last = p.mains.indexOfLast { vm.log[it.id]?.d != null }
        if (last < 0) null else p.mains.getOrNull(last + 1)?.let { nm ->
            nm to p.sides.count { it.afterId == p.mains[last].id && vm.log[it.id]?.d == null }
        }
    }
    val quick = (vm.subs.mapNotNull { Catalog.show(it) } + shows).distinctBy { it.id }.take(6)
    val tops = vm.log.entries.filter { it.value.d != null }.mapNotNull { Catalog.episode(it.key)?.pid }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    val feed = vm.feedItems.take(3)
    val nothing = vm.subs.isEmpty() && vm.log.isEmpty() && continuing.isEmpty()

    // this week against the week before, for the colour blocks and the streak
    val today = remember { LocalDate.now() }
    val listened = vm.log.entries.mapNotNull { e ->
        val d = e.value.d ?: return@mapNotNull null
        val ep = Catalog.episode(e.key) ?: return@mapNotNull null
        Triple(d, ep, daysAgo(d, today))
    }
    val thisWeek = listened.filter { (it.third ?: 99) in 0..6 }
    val lastWeek = listened.filter { (it.third ?: 99) in 7..13 }
    val dayset = listened.map { it.first }.toSet()
    val streak = Streaks.current(dayset, today)

    val listState = rememberLazyListState()
    val ambient = rememberArtTint(continuing.firstOrNull()?.let { showOf(it.pid).artworkUrl } ?: "")
    val hour = remember { java.time.LocalTime.now().hour }
    Box(Modifier.fillMaxSize()) {
    // a glow in the colour of what you were last listening to, fading as you scroll
    Box(
        Modifier.fillMaxWidth().height(360.dp)
            .graphicsLayer { alpha = if (listState.firstVisibleItemIndex > 0) 0f else (1f - listState.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(listOf(ambient.copy(alpha = 0.34f), Color.Transparent))),
    )
    LazyColumn(state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item(key = "title") {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp).appear(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LeestnLogo()
                Column(Modifier.appear(1), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(greeting(hour), style = MaterialTheme.typography.displaySmall)
                    Text(if (continuing.isNotEmpty()) "Pick up where you left off." else "What are we listening to?", style = MaterialTheme.typography.bodyMedium, color = LColors.Text2)
                }
            }
        }
        item(key = "update") { UpdateBanner(vm, openSettings = openSettings) }
        if (nothing) {
            item(key = "welcome") {
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(28.dp).padding(20.dp).appear(1),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Find something to listen to", style = MaterialTheme.typography.titleLarge)
                    Text("Search real podcasts, paste a Spotify or RSS link, or import your subscriptions. Everything you play, rate and list shows up here.", color = LColors.Text2, style = MaterialTheme.typography.bodyMedium)
                    Gap(4.dp)
                    ActionPill("Open Discover", Icons.Rounded.PlayArrow, primary = true, onClick = goDiscover)
                }
            }
        }
        if (thisWeek.isNotEmpty() || lastWeek.isNotEmpty()) {
            item(key = "week") {
                Column(Modifier.appear(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Your week", subtitle = "Last 7 days against the 7 before")
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ColorBlock("Episodes", "${thisWeek.size}", LColors.Lilac, Modifier.weight(1f), changeText(thisWeek.size, lastWeek.size))
                        val hNow = thisWeek.sumOf { it.second.min }
                        val hBefore = lastWeek.sumOf { it.second.min }
                        ColorBlock("Hours", String.format(java.util.Locale.US, "%.1f", hNow / 60.0), LColors.Mint, Modifier.weight(1f), changeText(hNow, hBefore))
                        val sNow = thisWeek.map { it.second.pid }.distinct().size
                        val sBefore = lastWeek.map { it.second.pid }.distinct().size
                        ColorBlock("Shows", "$sNow", LColors.Sky, Modifier.weight(1f), changeText(sNow, sBefore))
                    }
                }
            }
        }
        item(key = "continue") {
            AnimatedVisibility(
                visible = continuing.isNotEmpty(),
                enter = fadeIn(tween(Motion.Normal)) + expandVertically(tween(Motion.Normal)),
                exit = fadeOut(tween(Motion.Quick)) + shrinkVertically(tween(Motion.Quick)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Continue listening")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(continuing, key = { it.id }) { ep -> ResumeCard(vm, ep, Modifier.width(300.dp)) { openEp(ep.id) } }
                    }
                }
            }
        }
        if (fresh.isNotEmpty()) {
            item(key = "fresh") {
                Column(Modifier.appear(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("New episodes", subtitle = "From your shows")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(fresh, key = { it.id }) { ep -> EpisodeCard(vm, ep) { openEp(ep.id) } }
                    }
                }
            }
        }
        item(key = "streak") { StreakCard(streak, Streaks.week(dayset, today), Modifier.padding(horizontal = 16.dp).appear(2), onClick = goProfile) }
        if (quick.size >= 2) {
            item(key = "quick") {
                Column(Modifier.appear(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Your shows")
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { sp -> QuickTile(vm, sp, Modifier.weight(1f).height(56.dp)) { openShow(sp.id) } }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        if (ups.isNotEmpty()) {
            item(key = "ups") {
                Column(Modifier.appear(3), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Up next", subtitle = "In your stories")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(ups, key = { it.first.id }) { (ep, sides) -> UpNextCard(vm, ep, sides) { openEp(ep.id) } }
                    }
                }
            }
        }
        if (tops.isNotEmpty()) {
            item(key = "tops") {
                Column(Modifier.appear(3), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader("Your top shows", subtitle = "By episodes finished")
                    tops.forEachIndexed { i, e ->
                        Catalog.show(e.key)?.let { sp -> TopShowRow(i, sp, e.value, tops.first().value) { openShow(sp.id) } }
                    }
                }
            }
        }
        if (picks.isNotEmpty()) {
            item(key = "picks") {
                Column(Modifier.appear(3), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Picked for you", action = "Discover", onAction = goDiscover)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(picks, key = { it.id }) { p -> PosterCard(p, onClick = { openShow(p.id) }, rating = vm.showAverage(p.id)?.first) }
                    }
                }
            }
        }
        item(key = "trending") {
            Column(Modifier.appear(4), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Trending now", action = "Discover", onAction = goDiscover)
                when (val l = vm.trendingLoad) {
                    is Load.Failed -> ErrorState("Couldn't load the charts. ${l.message}", onRetry = { vm.loadTrending(force = true) })
                    else -> if (vm.trending.isEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), userScrollEnabled = false) {
                            items(5) { PosterCardSkeleton() }
                        }
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(vm.trending, key = { it.id }) { p -> PosterCard(p, onClick = { openShow(p.id) }, rating = vm.showAverage(p.id)?.first) }
                        }
                    }
                }
            }
        }
        if (vm.connected) item(key = "friends") {
            Column(Modifier.appear(5), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Friends", action = "Open feed", onAction = goFriends)
                if (feed.isEmpty()) {
                    Text("Follow people to see what they listen to and rate.", color = LColors.Text3, modifier = Modifier.padding(horizontal = 16.dp))
                }
                feed.forEach { FeedItem(vm, it, openItem = openItem, openPerson = openPerson) }
            }
        }
    }
    }
}
