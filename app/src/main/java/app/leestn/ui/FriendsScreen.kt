package app.leestn.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.leestn.data.Dates
import app.leestn.data.Podcast
import app.leestn.data.RFeedItem
import app.leestn.data.RPerson
import app.leestn.state.AppViewModel
import app.leestn.state.Load
import app.leestn.ui.theme.LColors
import kotlinx.coroutines.delay

/** One thing a person you follow listened to. */
@Composable
fun FeedItem(vm: AppViewModel, e: RFeedItem, openItem: (RFeedItem) -> Unit, openPerson: (String) -> Unit) {
    val show = Podcast(e.showId, e.showName.ifBlank { "Podcast" }, "", "", "", e.feedUrl, e.showArt)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LColors.Surface).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.clickable { openPerson(e.handle) }) { PersonAvatar(vm, e.handle, e.name, e.avatarV, 40.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(e.name.ifBlank { e.handle }) }
                append(if (e.rating != null) " rated an episode ${e.rating}/10" else " listened to an episode")
            }, style = MaterialTheme.typography.bodyMedium)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LColors.Surface2).clickable { openItem(e) }.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Poster(show, Modifier.width(36.dp), corner = 8.dp)
                Column(Modifier.weight(1f)) {
                    Text(e.epTitle.ifBlank { "Episode" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.showName, style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RatingPill(e.rating)
            }
            if (e.listenedAt.length >= 10) Text(Dates.rel(e.listenedAt.substring(0, 10)), style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
        }
    }
}

@Composable
private fun PersonRow(vm: AppViewModel, f: RPerson, openPerson: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { openPerson(f.handle) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonAvatar(vm, f.handle, f.name, f.avatarV, 42.dp)
        Column(Modifier.weight(1f)) {
            Text(f.name.ifBlank { f.handle }, style = MaterialTheme.typography.titleSmall)
            Text("@" + f.handle + (if (f.bio.isNotBlank()) " · " + f.bio else ""), style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Chip(if (f.following) "Following" else "Follow", !f.following) { vm.toggleFollow(f.handle) }
    }
}

/** The invite code for your server, with copy and share buttons. */
@Composable
fun InviteCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val code = vm.shareCode() ?: return
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Invite friends", style = MaterialTheme.typography.titleMedium)
        Text(
            "Send this code to a friend. In Leestn they open Settings, choose Join with a code, and paste it. They get transcripts from your server and can follow you.",
            style = MaterialTheme.typography.bodySmall, color = LColors.Text3,
        )
        Text(
            code, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LColors.Surface2).padding(10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill(if (copied) "Copied" else "Copy", Icons.Rounded.ContentCopy, active = copied) {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }
            ActionPill("Share", Icons.Rounded.Share) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join me on Leestn. Open Settings, choose Join with a code, and paste this:\n\n$code")
                }
                ctx.startActivity(Intent.createChooser(send, "Share invite"))
            }
        }
    }
}

@Composable
fun FriendsScreen(vm: AppViewModel, openItem: (RFeedItem) -> Unit, openPerson: (String) -> Unit, openSettings: () -> Unit) {
    if (!vm.connected) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Friends", style = MaterialTheme.typography.headlineMedium)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LColors.Surface).padding(20.dp).appear(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Groups, null, tint = LColors.Red)
                Text("Friends live on a Leestn server", style = MaterialTheme.typography.titleLarge)
                Text(
                    "There is no company server. You and your friends meet on a small server that one of you hosts for free on Cloudflare. It also generates transcripts for episodes that don't have one. Join with a code from a friend, or set up your own.",
                    style = MaterialTheme.typography.bodyMedium, color = LColors.Text2,
                )
                ActionPill("Set up or join a server", Icons.Rounded.Groups, primary = true, onClick = openSettings)
            }
        }
        return
    }

    var tab by rememberSaveable { mutableStateOf("feed") }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(vm.connected) { vm.loadFeed(force = true) }
    LaunchedEffect(query, tab) {
        if (tab == "people") {
            if (query.isNotEmpty()) delay(300)
            vm.searchPeople(query)
        }
    }
    val feed = vm.feedItems

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Friends", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Feed", tab == "feed") { tab = "feed" }
                    Chip("People", tab == "people") { tab = "people" }
                }
            }
        }
        if (tab == "feed") {
            val load = vm.feedLoad
            when {
                load is Load.Failed && feed.isEmpty() -> item { ErrorState(load.message, onRetry = { vm.loadFeed(force = true) }) }
                load is Load.Busy && feed.isEmpty() -> item { Spinner(Modifier.fillMaxWidth().padding(24.dp)) }
                feed.isEmpty() -> item {
                    EmptyState("Nothing here yet", "Follow friends in People. What they listen to and rate shows up here.")
                }
            }
            items(feed.size, key = { feed[it].key }) { i -> Box(Modifier.animateItem()) { FeedItem(vm, feed[i], openItem, openPerson) } }
        } else {
            item { InviteCard(vm, Modifier.padding(horizontal = 16.dp)) }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    placeholder = { Text("Search by name or @username") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(99.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LColors.Red, unfocusedBorderColor = LColors.Surface3, cursorColor = LColors.Red),
                )
            }
            val load = vm.peopleLoad
            when {
                load is Load.Failed && vm.people.isEmpty() -> item { ErrorState(load.message, onRetry = { vm.searchPeople(query) }) }
                load is Load.Busy && vm.people.isEmpty() -> item { Spinner(Modifier.fillMaxWidth().padding(24.dp)) }
                vm.people.isEmpty() -> item {
                    Text(
                        if (query.isBlank()) "No one else has joined yet. Share your invite code above." else "No one matches that.",
                        color = LColors.Text3, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(vm.people, key = { "p-" + it.handle }) { f -> Box(Modifier.animateItem()) { PersonRow(vm, f, openPerson) } }
        }
    }
}
