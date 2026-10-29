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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
private fun Section(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tint: Color = LColors.Accent, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxWidth().card(28.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
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
            colors = SwitchDefaults.colors(checkedTrackColor = LColors.Accent, checkedThumbColor = Color.White, uncheckedTrackColor = LColors.Surface3, uncheckedThumbColor = LColors.Text3, uncheckedBorderColor = Color.Transparent),
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
                ScreenTitle("Settings", subtitle = "Server, downloads and updates")
            }
        }
        item { ServerSection(vm) }
        item {
            Section("Favorite genres", Icons.Rounded.Favorite) {
                GenrePicker(picked, onToggle = { g -> if (g in picked) picked.remove(g) else picked.add(g) })
                if (changed) ActionPill("Update suggestions", Icons.Rounded.Check, primary = true) { vm.setGenres(picked.toList()) }
            }
        }
        item {
            Section("Downloads", Icons.Rounded.Download, LColors.Sky) {
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
            Section("About", Icons.Rounded.SystemUpdate, LColors.Lilac) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
                Text("Leestn $ver", style = MaterialTheme.typography.titleSmall)
                Text("Updates come from github.com/Hytyei/Leestn. Leestn checks for a newer version when you open it.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                UpdateControls(vm)
            }
        }
        item {
            Section("Data", Icons.Rounded.Storage, LColors.Mint) {
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
                ScreenTitle("Downloads", subtitle = "${entries.size} episode${if (entries.size == 1) "" else "s"} · ${formatBytes(Downloads.sizeBytes())}")
            }
        }
        if (entries.isEmpty()) item {
            EmptyState("Nothing downloaded", "Use Download on an episode, or turn on automatic downloads in Settings.")
        }
        items(entries, key = { it.key }) { (id, st) ->
            val ep = Catalog.episode(id)
            Column(Modifier.animateItem()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().card(24.dp).pressable { if (ep != null) openEp(id) }.padding(10.dp),
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
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Accent, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Accent),
    )
}

/** White "Continue with Google" button. Plain text mark, so it works without any extra image. */
@Composable
private fun GoogleButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(99.dp)
    Row(
        Modifier.clip(shape).background(if (enabled) Color.White else Color.White.copy(alpha = 0.35f)).pressable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = Color(0xFF1F1F1F), style = MaterialTheme.typography.labelLarge)
    }
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
    val context = LocalContext.current

    Section("Friends and transcripts", Icons.Rounded.Groups) {
        if (vm.connected) {
            Text("Connected as @${vm.serverHandle}", style = MaterialTheme.typography.titleSmall)
            Text(
                vm.serverUrl.removePrefix("https://") + "\nYour server syncs your log and ratings, runs the friends feed, and generates transcripts when you press Generate on an episode. Transcripts are shared, so each episode is only processed once for everyone on the server.",
                style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
            )
            TranscriptionAccess(vm)
            ShareInvites(vm)
            HorizontalDivider(color = LColors.Line)
            Text("Google account", style = MaterialTheme.typography.titleSmall)
            if (vm.googleEmail.isNotBlank()) {
                Text("Linked to ${vm.googleEmail}. You can sign in to this account with Google on any phone.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                ActionPill("Disconnect Google", Icons.Rounded.Close) { vm.unlinkGoogle() }
            } else {
                Text(
                    "Connect your Google account to this one, so you can sign back in on a new phone without anything else. Your username, ratings and log stay exactly as they are.",
                    style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
                )
                if (busy !is Load.Busy) GoogleButton("Connect Google account") { vm.linkGoogle(context) }
            }
            if (busy is Load.Failed) Text(busy.message, color = LColors.Err, style = MaterialTheme.typography.bodySmall)
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
                if (code.isNotBlank() && parsed == null) Text("That doesn't look like a Leestn code.", color = LColors.Err, style = MaterialTheme.typography.bodySmall)
                if (parsed != null) Text(
                    "Server: " + parsed.first.removePrefix("https://") + if (parsed.second.isBlank()) "\nFriends only. Add the invite key later in Settings to use transcription." else "\nIncludes transcription access.",
                    style = MaterialTheme.typography.bodySmall, color = LColors.Text2,
                )
            } else {
                Text("Deploy the backend folder to your Cloudflare account (see the README), then enter its address. The invite key is only for Whisper transcription, so it is optional here and you can add it later.", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                TextBox(url, { url = it }, "https://leestn-api.yourname.workers.dev")
                TextBox(inviteKey, { inviteKey = it }, "Invite key (optional, unlocks transcription)")
            }
            TextBox(handle, { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, "Choose a username (letters, numbers, _)")
            TextBox(shown, { shown = it }, "Display name")
            if (busy is Load.Failed) Text(busy.message, color = LColors.Err, style = MaterialTheme.typography.bodySmall)
            if (busy is Load.Busy) Spinner(Modifier.fillMaxWidth())
            else ActionPill(if (mode == "join") "Join" else "Connect", Icons.Rounded.Groups, primary = true) {
                if (mode == "join") {
                    if (parsed != null) vm.connectServer(parsed.first, parsed.second, handle, shown)
                } else vm.connectServer(url, inviteKey, handle, shown)
            }
            if (busy !is Load.Busy) {
                Text(
                    "Or use Google. New here? Choose a username above first. Already linked your Google account? Just tap the button, no username needed.",
                    style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
                )
                val ready = if (mode == "join") parsed != null else url.isNotBlank()
                GoogleButton("Continue with Google", enabled = ready) {
                    if (mode == "join") parsed?.let { vm.googleConnect(context, it.first, it.second, handle, shown) }
                    else vm.googleConnect(context, url, inviteKey, handle, shown)
                }
            }
        }
    }
}

/** Whisper transcription is invite only. Shows whether this account has it, and lets you enter the invite key to unlock it. */
@Composable
private fun TranscriptionAccess(vm: AppViewModel) {
    var key by rememberSaveable { mutableStateOf("") }
    val busy = vm.serverBusy
    HorizontalDivider(color = LColors.Line)
    Text("Transcription", style = MaterialTheme.typography.titleSmall)
    if (vm.canTranscribe) {
        Text("Unlocked. You can generate transcripts with the server's Whisper.", style = MaterialTheme.typography.bodySmall, color = LColors.Good)
        UsageMeter(vm)
    } else {
        Text(
            "Locked. Friends, the feed and your synced log work without it. Whisper transcription is invite only: enter the invite key from whoever runs the server.",
            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
        )
        TextBox(key, { key = it }, "Invite key")
        if (busy is Load.Busy) Spinner(Modifier.fillMaxWidth())
        else ActionPill("Unlock transcription", androidx.compose.material.icons.Icons.Rounded.Lock, primary = true) { vm.unlockTranscription(key); key = "" }
    }
}

/** Two separate things to share: a friends link (anyone can join) and a transcription invite (includes the key). */
@Composable
private fun ShareInvites(vm: AppViewModel) {
    val ctx = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val friends = vm.friendsCode() ?: return
    val transcribe = vm.transcribeCode()
    var copied by remember { mutableStateOf("") }
    fun share(code: String, text: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "$text\n\n$code")
        }
        ctx.startActivity(android.content.Intent.createChooser(send, "Share"))
    }
    HorizontalDivider(color = LColors.Line)
    Text("Invite friends", style = MaterialTheme.typography.titleSmall)
    Text(
        "Anyone with the friends link can join and follow each other. It never includes the invite key, so it does not give transcription.",
        style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionPill(if (copied == "f") "Copied" else "Copy link", androidx.compose.material.icons.Icons.Rounded.ContentCopy, active = copied == "f") {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(friends)); copied = "f"
        }
        ActionPill("Share friends link", androidx.compose.material.icons.Icons.Rounded.Share) {
            share(friends, "Join me on Leestn. Open Settings, choose Join with a code, and paste this:")
        }
    }
    if (transcribe != null) {
        Text(
            "The transcription invite also contains the server's invite key. Only send it to people you trust to use your free Whisper time.",
            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill(if (copied == "t") "Copied" else "Copy invite", androidx.compose.material.icons.Icons.Rounded.ContentCopy, active = copied == "t") {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(transcribe)); copied = "t"
            }
            ActionPill("Share transcription invite", androidx.compose.material.icons.Icons.Rounded.Share) {
                share(transcribe, "Join me on Leestn, with transcription. Open Settings, choose Join with a code, and paste this:")
            }
        }
    }
}
