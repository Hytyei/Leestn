package app.leestn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Groups
import app.leestn.data.ShareCode
import app.leestn.state.Load
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.leestn.data.Catalog
import app.leestn.data.Dl
import app.leestn.data.Downloads
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f GB", b / 1e9)
    b >= 1_000_000 -> String.format(java.util.Locale.US, "%.0f MB", b / 1e6)
    b > 0 -> "${b / 1000} KB"
    else -> "0 MB"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LColors.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
        }
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = LColors.Red, checkedThumbColor = Color.White, uncheckedTrackColor = LColors.Surface3, uncheckedThumbColor = LColors.Text3, uncheckedBorderColor = Color.Transparent),
        )
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, openDownloads: () -> Unit) {
    val picked = remember { mutableStateListOf<String>().also { it.addAll(vm.genres) } }
    var confirmErase by remember { mutableStateOf(false) }
    val changed = picked.toSet() != vm.genres.toSet()
    val dlCount = Downloads.state.values.count { it is Dl.Done }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item { ServerSection(vm) }
        item {
            Section("Favorite genres") {
                GenrePicker(picked, onToggle = { g -> if (g in picked) picked.remove(g) else picked.add(g) })
                if (changed) ActionPill("Update suggestions", Icons.Rounded.Check, primary = true) { vm.setGenres(picked.toList()) }
            }
        }
        item {
            Section("Downloads") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keep the next episodes downloaded", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "After you start or finish an episode, Leestn downloads the next unlistened main episodes of that show so they are ready offline.",
                        style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0, 1, 2, 3, 5, 10)) { n -> Chip(if (n == 0) "Off" else "$n", vm.autoDl == n) { vm.changeAutoDl(n) } }
                    }
                }
                HorizontalDivider(color = LColors.Line)
                SwitchRow("Wi-Fi only", "Don't download over mobile data", vm.wifiOnly) { vm.changeWifiOnly(it) }
                HorizontalDivider(color = LColors.Line)
                SwitchRow("Delete after listening", "Remove the offline copy once you finish an episode or mark it listened", vm.deleteAfter) { vm.changeDeleteAfter(it) }
                HorizontalDivider(color = LColors.Line)
                Row(Modifier.fillMaxWidth().pressable(onClick = openDownloads), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Manage downloads", style = MaterialTheme.typography.titleSmall)
                        Text("$dlCount episode${if (dlCount == 1) "" else "s"} · ${formatBytes(Downloads.sizeBytes())}", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                    }
                    Icon(Icons.Rounded.Download, null, tint = LColors.Text2)
                }
            }
        }
        item {
            Section("About") {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
                Text("Leestn $ver", style = MaterialTheme.typography.titleSmall)
                Text("Updates come from github.com/Hytyei/Leestn. Leestn checks for a newer version when you open it.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                UpdateControls(vm)
            }
        }
        item {
            Section("Data") {
                Text("Your log, ratings and lists are stored on this device" + (if (vm.connected) " and synced to your server." else ". Join a server to sync them and share with friends."), style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                ActionPill(if (confirmErase) "Confirm erase" else "Erase my data", Icons.Rounded.Delete, active = confirmErase) {
                    if (confirmErase) { vm.resetAll(); Downloads.removeAll(Catalog.appContext()); confirmErase = false } else confirmErase = true
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(vm: AppViewModel, onBack: () -> Unit, openEp: (String) -> Unit) {
    val entries = Downloads.state.entries.toList().sortedBy { (it.value is Dl.Done).not() }
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${entries.size} EPISODE${if (entries.size == 1) "" else "S"} · ${formatBytes(Downloads.sizeBytes())}", style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
                    Text("Downloads", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        if (entries.isEmpty()) item {
            EmptyState("Nothing downloaded", "Use Download on an episode, or turn on automatic downloads in Settings.")
        }
        items(entries, key = { it.key }) { (id, st) ->
            val ep = Catalog.episode(id)
            Column(Modifier.animateItem()) {
                Row(
                    Modifier.fillMaxWidth().pressable { if (ep != null) openEp(id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (ep != null) Poster(showOf(ep.pid), Modifier.size(48.dp), corner = 10.dp) else Box(Modifier.size(48.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ep?.title ?: "Episode", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when (st) {
                            is Dl.Done -> Text("Ready offline", style = MaterialTheme.typography.bodySmall, color = LColors.Good)
                            is Dl.Progress -> { ThinBar(st.frac); Text("Downloading ${(st.frac * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = LColors.Text3) }
                            else -> Text("Waiting for a connection", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                        }
                    }
                    IconButton(onClick = { vm.removeDownload(id) }) { Icon(if (st is Dl.Done) Icons.Rounded.Delete else Icons.Rounded.Close, "Remove", tint = LColors.Text3) }
                }
                HorizontalDivider(color = LColors.Line, modifier = Modifier.padding(start = 16.dp))
            }
        }
        if (entries.isNotEmpty()) item {
            Row(Modifier.padding(16.dp)) {
                ActionPill(if (confirm) "Confirm delete all" else "Delete all", Icons.Rounded.Delete, active = confirm) {
                    if (confirm) { Downloads.removeAll(Catalog.appContext()); confirm = false } else confirm = true
                }
            }
        }
    }
}


@Composable
private fun TextBox(value: String, onChange: (String) -> Unit, placeholder: String, single: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = single, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Red, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Red),
    )
}

/** Join a friend's server with a code, or connect to a server you host yourself. */
@Composable
private fun ServerSection(vm: AppViewModel) {
    var mode by rememberSaveable { mutableStateOf("join") }
    var code by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var inviteKey by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableStateOf(vm.profileName) }
    var confirmLeave by remember { mutableStateOf(false) }
    val busy = vm.serverBusy

    Section("Friends and transcripts") {
        if (vm.connected) {
            Text("Connected as @${vm.serverHandle}", style = MaterialTheme.typography.titleSmall)
            Text(
                vm.serverUrl.removePrefix("https://") + "\nYour server syncs your log and ratings, runs the friends feed, and generates transcripts when you press Generate on an episode. Transcripts are shared, so each episode is only processed once for everyone on the server.",
                style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
            )
            UsageMeter(vm)
            InviteCard(vm)
            if (busy is Load.Failed) Text(busy.message, color = LColors.Red, style = MaterialTheme.typography.bodySmall)
            if (busy is Load.Busy) Spinner(Modifier.fillMaxWidth())
            HorizontalDivider(color = LColors.Line)
            Text(
                "Leaving deletes your account and the data you synced from the server. Your log stays on this device.",
                style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
            )
            ActionPill(if (confirmLeave) "Confirm: delete account and leave" else "Leave server", Icons.Rounded.Delete, active = confirmLeave) {
                if (confirmLeave) { vm.leaveServer(); confirmLeave = false } else confirmLeave = true
            }
        } else {
            Text(
                "There is no company server. You and your friends share a small Leestn server that one of you hosts for free on Cloudflare. It holds the friends feed and the generated transcripts.",
                style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Join with a code", mode == "join") { mode = "join"; vm.clearServerMessage() }
                Chip("Host my own", mode == "host") { mode = "host"; vm.clearServerMessage() }
            }
            val parsed = if (mode == "join") ShareCode.decode(code) else null
            if (mode == "join") {
                TextBox(code, { code = it }, "Paste the code a friend sent you", single = false)
                if (code.isNotBlank() && parsed == null) Text("That doesn't look like a Leestn code.", color = LColors.Red, style = MaterialTheme.typography.bodySmall)
                if (parsed != null) Text("Server: " + parsed.first.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = LColors.Text2)
            } else {
                Text("Deploy the backend folder to your Cloudflare account (see the README), then enter its address and the invite key you chose.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                TextBox(url, { url = it }, "https://leestn-api.yourname.workers.dev")
                TextBox(inviteKey, { inviteKey = it }, "Invite key")
            }
            TextBox(handle, { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, "Choose a username (letters, numbers, _)")
            TextBox(shown, { shown = it }, "Display name")
            if (busy is Load.Failed) Text(busy.message, color = LColors.Red, style = MaterialTheme.typography.bodySmall)
            if (busy is Load.Busy) Spinner(Modifier.fillMaxWidth())
            else ActionPill(if (mode == "join") "Join" else "Connect", Icons.Rounded.Groups, primary = true) {
                if (mode == "join") {
                    if (parsed != null) vm.connectServer(parsed.first, parsed.second, handle, shown)
                } else vm.connectServer(url, inviteKey, handle, shown)
            }
        }
    }
}
