package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.leestn.data.Catalog
import app.leestn.data.ListItem
import app.leestn.state.AppViewModel
import app.leestn.state.UserList
import app.leestn.ui.theme.LColors

private fun pidOf(item: ListItem): String = when (item) {
    is ListItem.Ep -> Catalog.episode(item.id)?.pid ?: ""
    is ListItem.Show -> item.id
}

/** A small fan of up to three posters, like a hand of cards. */
@Composable
private fun Fan(list: UserList, modifier: Modifier = Modifier) {
    val first = list.items.take(3)
    Box(modifier.width(112.dp).height(84.dp)) {
        if (first.isEmpty()) {
            Box(Modifier.align(Alignment.CenterStart).size(72.dp).clip(RoundedCornerShape(16.dp)).background(LColors.Surface2))
        }
        first.asReversed().forEachIndexed { rev, item ->
            val i = first.size - 1 - rev
            Poster(
                showOf(pidOf(item)),
                Modifier.align(Alignment.CenterStart).offset(x = (i * 20).dp).size(72.dp).graphicsLayer { rotationZ = (i - 1) * 4f },
                corner = 14.dp,
            )
        }
    }
}

@Composable
fun ListsScreen(vm: AppViewModel, openList: (String) -> Unit, openDownloads: () -> Unit = {}) {
    var name by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    val all = vm.lists.toList()
    val shown = when (tab) {
        1 -> all.filter { l -> l.items.any { it is ListItem.Show } }
        2 -> all.filter { l -> l.items.any { it is ListItem.Ep } }
        else -> all
    }
    val offline = app.leestn.data.Downloads.state.values.count { it is app.leestn.data.Dl.Done }
    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ScreenTitle("Lists", Modifier.appear(), subtitle = "${all.size} list${if (all.size == 1) "" else "s"}") }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorBlock("Lists", "${all.size}", LColors.Lilac, Modifier.weight(1f))
                ColorBlock("Items", "${all.sumOf { it.items.size }}", LColors.Mint, Modifier.weight(1f))
                ColorBlock("Offline", "$offline", LColors.Sky, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 40) name = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("Name a new list") }, shape = RoundedCornerShape(99.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LColors.Accent, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Accent,
                        focusedContainerColor = LColors.Surface, unfocusedContainerColor = LColors.Surface,
                    ),
                )
                ActionPill("Create", Icons.Rounded.Add, primary = name.isNotBlank()) {
                    if (name.isNotBlank()) { vm.createList(name); name = "" }
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().card(24.dp).pressable(onClick = openDownloads).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Download, null, tint = LColors.Accent)
                Column(Modifier.weight(1f)) {
                    Text("Downloads", style = MaterialTheme.typography.titleSmall)
                    Text("$offline episode${if (offline == 1) "" else "s"} available offline", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = LColors.Text3)
            }
        }
        item { PillTabs(listOf("All", "Shows", "Episodes"), tab, { tab = it }) }
        if (shown.isEmpty()) item {
            Text("No lists here yet.", color = LColors.Text3, modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(shown, key = { it.id }) { l ->
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().animateItem().card(24.dp).pressable { openList(l.id) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Fan(l)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(l.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text("${l.items.size} item${if (l.items.size == 1) "" else "s"}${if (l.id == "wishlist") " · default" else ""}", style = MaterialTheme.typography.labelMedium, color = LColors.Text3)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = LColors.Text3)
            }
        }
    }
}

@Composable
fun ListDetailScreen(vm: AppViewModel, listId: String, onBack: () -> Unit, openEp: (String) -> Unit, openShow: (String) -> Unit) {
    val list = vm.lists.firstOrNull { it.id == listId }
    var confirm by remember { mutableStateOf(false) }
    if (list == null) {
        Column(Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("This list no longer exists.", color = LColors.Text3)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${list.items.size} ITEM${if (list.items.size == 1) "" else "S"}", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                    Text(list.name, style = MaterialTheme.typography.displaySmall)
                }
            }
        }
        if (list.items.isEmpty()) {
            item { Text("This list is empty. Use List on any episode or show.", color = LColors.Text3, modifier = Modifier.padding(16.dp)) }
        }
        items(list.items.toList(), key = { it.toString() }) { item ->
            Column(Modifier.animateItem()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    when (item) {
                        is ListItem.Ep -> {
                            val ep = Catalog.episode(item.id)
                            if (ep != null) EpisodeRow(vm, ep, onOpen = { openEp(item.id) })
                            else Text("Loading episode…", color = LColors.Text3, modifier = Modifier.padding(16.dp))
                        }
                        is ListItem.Show -> {
                            val p = showOf(item.id)
                            Row(
                                Modifier.fillMaxWidth().clickable { openShow(p.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Poster(p, Modifier.size(48.dp), corner = 10.dp)
                                Column {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Whole show · ${p.genre}", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = { vm.toggleInList(list.id, item) }) { Icon(Icons.Rounded.Close, "Remove from list", tint = LColors.Text3) }
            }
            HorizontalDivider(color = LColors.Line, modifier = Modifier.padding(start = 16.dp))
            }
        }
        if (list.id != "wishlist") {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (confirm) {
                        ActionPill("Confirm delete", Icons.Rounded.Close, primary = true) { vm.deleteList(list.id); onBack() }
                        ActionPill("Keep it", Icons.AutoMirrored.Rounded.ArrowBack) { confirm = false }
                    } else {
                        ActionPill("Delete list", Icons.Rounded.Close) { confirm = true }
                    }
                }
            }
        }
    }
}
