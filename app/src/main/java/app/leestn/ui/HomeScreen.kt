package app.leestn.ui

import androidx.compose.animation.AnimatedVisibility
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

@Composable
fun HomeScreen(
    vm: AppViewModel,
    openShow: (String) -> Unit,
    openEp: (String) -> Unit,
    openItem: (app.leestn.data.RFeedItem) -> Unit,
    openPerson: (String) -> Unit,
    goFriends: () -> Unit,
    goDiscover: () -> Unit,
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
    val feed = vm.feedItems.take(3)
    val nothing = vm.subs.isEmpty() && vm.log.isEmpty() && continuing.isEmpty()

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item(key = "title") {
            Row(Modifier.padding(horizontal = 16.dp).appear(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("leestn", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(LColors.Red))
            }
        }
        if (nothing) {
            item(key = "welcome") {
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LColors.Surface).padding(20.dp).appear(1),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Find something to listen to", style = MaterialTheme.typography.titleLarge)
                    Text("Search real podcasts, paste a Spotify or RSS link, or import your subscriptions. Everything you play, rate and list shows up here.", color = LColors.Text2, style = MaterialTheme.typography.bodyMedium)
                    Gap(4.dp)
                    ActionPill("Open Discover", Icons.Rounded.PlayArrow, primary = true, onClick = goDiscover)
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
                    continuing.forEach { ep ->
                        val p = showOf(ep.pid)
                        val secs = vm.prog[ep.id] ?: 0
                        Row(
                            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LColors.Surface).clickable { openEp(ep.id) }.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Poster(p, Modifier.width(64.dp), corner = 10.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${p.name} · ${ep.code}".uppercase(), style = MaterialTheme.typography.labelSmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ep.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (ep.seconds > 0) {
                                    ThinBar(secs / ep.seconds.toFloat())
                                    Text("${Dates.clock(secs)} of ${Dates.clock(ep.seconds)}", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                                } else {
                                    Text("${Dates.clock(secs)} in", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                                }
                            }
                            Row(
                                Modifier.clip(RoundedCornerShape(99.dp)).background(LColors.Red).pressable { vm.play(ep.id) }.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Resume", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        if (fresh.isNotEmpty()) {
            item(key = "fresh") {
                Column(Modifier.appear(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("New from your shows")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(fresh, key = { it.id }) { ep -> EpisodeCard(vm, ep) { openEp(ep.id) } }
                    }
                }
            }
        }
        if (ups.isNotEmpty()) {
            item(key = "ups") {
                Column(Modifier.appear(3), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Up next in your stories")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(ups, key = { it.first.id }) { (ep, sides) -> UpNextCard(vm, ep, sides) { openEp(ep.id) } }
                    }
                }
            }
        }
        if (picks.isNotEmpty()) {
            item(key = "picks") {
                Column(Modifier.appear(3), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Picked for you", action = "Discover", onAction = goDiscover)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
                            items(5) { PosterCardSkeleton() }
                        }
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
