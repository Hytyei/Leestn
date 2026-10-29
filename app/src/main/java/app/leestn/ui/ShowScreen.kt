package app.leestn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.leestn.data.Catalog
import app.leestn.data.Kind
import app.leestn.data.ListItem
import app.leestn.data.StatsCalc
import app.leestn.state.AppViewModel
import app.leestn.state.Load
import app.leestn.ui.theme.LColors

@Composable
internal fun ActionPill(label: String, icon: ImageVector, active: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(99.dp)
    val bg by animateColorAsState(when { primary -> LColors.Red; active -> LColors.RedSoft; else -> Color.Transparent }, tween(Motion.Quick), label = "pillBg")
    val fg by animateColorAsState(when { primary -> Color.White; active -> LColors.Red; else -> LColors.Text }, tween(Motion.Quick), label = "pillFg")
    val line by animateColorAsState(if (primary || active) LColors.Red else LColors.Surface3, tween(Motion.Quick), label = "pillLine")
    Row(
        Modifier.clip(shape).background(bg)
            .border(BorderStroke(1.dp, line), shape)
            .pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ShowScreen(vm: AppViewModel, pid: String, onBack: () -> Unit, openEp: (String) -> Unit) {
    LaunchedEffect(pid) { vm.ensureShow(pid) }
    val p = Catalog.show(pid)
    if (p == null) {
        Column(Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("This show isn't available.", color = LColors.Text3)
        }
        return
    }
    var cat by rememberSaveable(pid) { mutableStateOf("all") }
    var order by rememberSaveable { mutableStateOf("story") }
    var sheet by remember { mutableStateOf(false) }
    var more by rememberSaveable(pid) { mutableStateOf(false) }
    val load = vm.showLoad[pid]
    val (done, total) = StatsCalc.completion(vm.log.toMap(), p)
    val next = p.mains.firstOrNull { vm.log[it.id]?.d == null }
    val wish = vm.inWishlist(ListItem.Show(pid))
    val subbed = vm.isSubscribed(pid)

    val shown = when {
        cat != "all" -> p.eps.filter { it.cat.id == cat }
        order == "story" -> p.story
        else -> p.eps.sortedBy { it.date }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val tint = rememberArtTint(p.artworkUrl)
    LazyColumn(
        state = listState,
        modifier = Modifier.drawBehind {
            val a = if (listState.firstVisibleItemIndex > 0) 0f else (1f - listState.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 1f)
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = 0.40f * a), androidx.compose.ui.graphics.Color.Transparent), endY = 380.dp.toPx()))
        },
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "head") {
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 20.dp).appear(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Poster(p, Modifier.width(180.dp).glow(tint, 28.dp, 0.45f), showName = true, corner = 28.dp)
                    Gap(6.dp)
                    Text("${p.genre.ifBlank { "Podcast" }.uppercase()}${if (p.host.isNotBlank()) " · " + p.host.uppercase() else ""}", style = MaterialTheme.typography.labelSmall, color = LColors.Text3, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(p.name.ifBlank { "Podcast" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    vm.showAverage(pid)?.let { avg ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RatingChip(avg.first)
                            Text("your average · ${avg.second} rated", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                        }
                    }
                    if (p.loaded) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorBlock("Main", "${p.mains.size}", LColors.Lilac, Modifier.width(96.dp))
                            ColorBlock("Side", "${p.sides.size}", LColors.Mint, Modifier.width(96.dp))
                            ColorBlock("Seasons", "${p.cats.size}", LColors.Sky, Modifier.width(96.dp))
                        }
                    }
                }
            }
        }
        item(key = "about") {
            val text = p.description.ifBlank { p.tagline }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).animateContentSize(tween(Motion.Normal))) {
                if (text.isNotBlank()) Column(Modifier.fillMaxWidth().card(24.dp).padding(16.dp)) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = LColors.Text2, maxLines = if (more) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                    if (text.length > 160) {
                        Text(if (more) "Show less" else "Show more", color = LColors.Red, style = MaterialTheme.typography.labelLarge, modifier = Modifier.pressable { more = !more }.padding(vertical = 6.dp))
                    }
                }
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ActionPill(if (subbed) "Subscribed" else "Subscribe", if (subbed) Icons.Rounded.Check else Icons.Rounded.Add, active = subbed, primary = !subbed) { vm.toggleSub(pid) }
                    }
                    if (next != null) item {
                        ActionPill(if (done > 0) "Continue ${next.code}" else "Start ${next.code}", Icons.Rounded.PlayArrow) { vm.play(next.id) }
                    }
                    item {
                        ActionPill(if (wish) "In wishlist" else "Wishlist", if (wish) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, active = wish) {
                            vm.toggleInList("wishlist", ListItem.Show(pid))
                        }
                    }
                    item { ActionPill("List", Icons.Rounded.Bookmarks) { sheet = true } }
                }
                if (p.loaded && total > 0) {
                    Column(Modifier.fillMaxWidth().card(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThinBar(done / total.toFloat())
                        Text("You have finished $done of $total main episodes", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                    }
                }
                AnimatedVisibility(load is Load.Busy && p.loaded, enter = fadeIn(), exit = fadeOut()) {
                    Text("Checking for new episodes…", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                }
            }
        }
        if (!p.loaded) {
            item(key = "state") {
                when (load) {
                    is Load.Failed -> ErrorState(load.message, onRetry = { vm.ensureShow(pid, force = true) })
                    else -> Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(6) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SkeletonBox(Modifier.size(width = 48.dp, height = 30.dp), 8.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SkeletonBox(Modifier.fillMaxWidth(0.8f).height(12.dp), 4.dp)
                                    SkeletonBox(Modifier.fillMaxWidth(0.5f).height(10.dp), 4.dp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "ratings") {
                Column(Modifier.padding(vertical = 8.dp)) { ShowRatingsPanel(vm, p, openEp) }
            }
            item(key = "cats") {
                val tabs = listOf("all" to "All (${p.eps.size})") + p.cats.map { c -> c.id to "${c.name} (${p.eps.count { it.cat.id == c.id }})" }
                PillTabs(
                    tabs.map { it.second }, tabs.indexOfFirst { it.first == cat }.coerceAtLeast(0), { cat = tabs[it].first },
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                )
            }
            if (cat == "all") {
                item(key = "order") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Story order", order == "story") { order = "story" }
                            Chip("Release order", order == "release") { order = "release" }
                        }
                        Text(
                            if (order == "story") "Side content sits right after the main episode it fits best." else "Exactly as each episode was released.",
                            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { ep ->
                val ctx = if (cat == "all" && ep.kind == Kind.SIDE && ep.afterId != null) {
                    Catalog.episode(ep.afterId)?.let { "Side content · best after ${it.code}" }
                } else null
                Column(Modifier.animateItem()) {
                    EpisodeRow(vm, ep, onOpen = { openEp(ep.id) }, ctx = ctx)
                    HorizontalDivider(color = LColors.Line, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
    if (sheet) AddToListSheet(vm, ListItem.Show(pid)) { sheet = false }
}
