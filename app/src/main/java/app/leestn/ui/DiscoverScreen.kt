package app.leestn.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.leestn.state.AppViewModel
import app.leestn.state.Load
import app.leestn.ui.theme.LColors
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(vm: AppViewModel, openShow: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("All") }
    var dialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.loadTrending(); vm.loadForYou() }
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(400)
        vm.search(query)
    }
    // jump straight to a show added by link
    LaunchedEffect(vm.addedId) {
        val id = vm.addedId
        if (id != null) { dialog = false; vm.clearAdd(); openShow(id) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) { null }
            if (text != null) vm.importOpml(text)
        }
    }

    val searching = query.trim().length >= 2
    val base = if (searching) vm.results else vm.trending
    val genres = base.map { it.genre }.filter { it.isNotBlank() }.distinct().take(12)
    val shown = if (genre == "All") base else base.filter { it.genre == genre }
    val load = if (searching) vm.resultsLoad else vm.trendingLoad

    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Discover", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    ActionPill("Add show", Icons.Rounded.Add) { dialog = true }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; genre = "All" },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search every podcast") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(99.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LColors.Red,
                        unfocusedBorderColor = LColors.Surface3,
                        focusedContainerColor = LColors.Surface,
                        unfocusedContainerColor = LColors.Surface,
                        cursorColor = LColors.Red,
                    ),
                )
                AnimatedVisibility(genres.size > 1, enter = fadeIn(tween(Motion.Normal)), exit = fadeOut(tween(Motion.Quick))) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        items(listOf("All") + genres, key = { it }) { g -> Chip(g, genre == g) { genre = g } }
                    }
                }
                if (searching) Text("Results for “${query.trim()}”", style = MaterialTheme.typography.titleLarge, modifier = Modifier.animateContentSize())
                else if (vm.forYou.isEmpty()) Text("Trending now", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (!searching && genre == "All") {
            if (vm.genres.isNotEmpty() && vm.forYouLoad is Load.Busy && vm.forYou.isEmpty()) {
                item(key = "fy-skeleton", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(20.dp), 6.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { PosterCardSkeleton(Modifier.weight(1f)) } }
                    }
                }
            }
            vm.forYou.entries.toList().forEach { (g, list) ->
                item(key = "fy-$g", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.animateItem().appear(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Because you like $g", style = MaterialTheme.typography.titleLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(list.take(14), key = { it.id }) { p -> PosterCard(p, onClick = { openShow(p.id) }, rating = vm.showAverage(p.id)?.first) }
                        }
                    }
                }
            }
            if (vm.forYou.isNotEmpty()) {
                item(key = "trend-title", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Trending now", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        when {
            load is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(load.message, onRetry = { if (searching) vm.search(query) else vm.loadTrending(force = true) })
            }
            load is Load.Busy && shown.isEmpty() -> items(9) { PosterCardSkeleton(Modifier.fillMaxWidth()) }
            shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmptyState(
                        if (searching) "Nothing found" else "No charts yet",
                        if (searching) "Search uses Apple's podcast directory, so new or private shows may be missing. Add yours with its RSS feed link." else "Pull the charts again in a moment.",
                    )
                    if (searching) ActionPill("Add by RSS or link", Icons.Rounded.Add) { dialog = true }
                }
            }
            else -> itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                PosterCard(
                    p, onClick = { openShow(p.id) }, rating = vm.showAverage(p.id)?.first,
                    modifier = Modifier.fillMaxWidth().animateItem().let { if (i < 12) it.appear(i, 30) else it },
                )
            }
        }
    }

    if (dialog) {
        var link by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = false; vm.clearAdd() },
            containerColor = LColors.Surface,
            title = { Text("Add a show") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateContentSize()) {
                    Text(
                        "Paste an RSS feed link (best, works for any show, including your own), a podcast website, an Apple Podcasts link or a Spotify show link. Spotify doesn't share episode audio with other apps, so for Spotify links Leestn looks the show up in Apple's directory.",
                        style = MaterialTheme.typography.bodySmall, color = LColors.Text2,
                    )
                    OutlinedTextField(
                        value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…") }, leadingIcon = { Icon(Icons.Rounded.Link, null) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Red, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Red),
                    )
                    when (val l = vm.addLoad) {
                        is Load.Busy -> Text("Looking it up…", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                        is Load.Failed -> Text(l.message, style = MaterialTheme.typography.bodySmall, color = LColors.Red)
                        else -> vm.addMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LColors.Good) }
                    }
                    ActionPill("Import OPML file", Icons.Rounded.FileUpload) { picker.launch(arrayOf("*/*")) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addFromLink(link) }, enabled = link.isNotBlank() && vm.addLoad !is Load.Busy) { Text("Add") } },
            dismissButton = { TextButton(onClick = { dialog = false; vm.clearAdd() }) { Text("Close") } },
        )
    }
}
