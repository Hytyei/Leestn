package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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

@Composable
private fun Mosaic(list: UserList) {
    val first = list.items.take(4)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (col in 0..1) {
                    val item = first.getOrNull(row * 2 + col)
                    if (item != null) {
                        Poster(showOf(pidOf(item)), Modifier.weight(1f), corner = 10.dp)
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(LColors.Surface2))
                    }
                }
            }
        }
    }
}

@Composable
fun ListsScreen(vm: AppViewModel, openList: (String) -> Unit, openDownloads: () -> Unit = {}) {
    var name by remember { mutableStateOf("") }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Lists", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name, onValueChange = { if (it.length <= 40) name = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text("Name a new list") }, shape = RoundedCornerShape(99.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Red, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Red),
                    )
                    ActionPill("Create", Icons.Rounded.Add, primary = name.isNotBlank()) {
                        if (name.isNotBlank()) { vm.createList(name); name = "" }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val n = app.leestn.data.Downloads.state.values.count { it is app.leestn.data.Dl.Done }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LColors.Surface).pressable(onClick = openDownloads).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Download, null, tint = LColors.Red)
                Column(Modifier.weight(1f)) {
                    Text("Downloads", style = MaterialTheme.typography.titleSmall)
                    Text("$n episode${if (n == 1) "" else "s"} available offline", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                }
            }
        }
        items(vm.lists.toList(), key = { it.id }) { l ->
            Column(
                Modifier.animateItem().clip(RoundedCornerShape(18.dp)).background(LColors.Surface).pressable { openList(l.id) }.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Mosaic(l)
                Text(l.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text("${l.items.size} item${if (l.items.size == 1) "" else "s"}${if (l.id == "wishlist") " · default" else ""}", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
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
                    Text(list.name, style = MaterialTheme.typography.headlineMedium)
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
