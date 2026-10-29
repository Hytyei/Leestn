package app.leestn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.leestn.data.BadgeProgress
import app.leestn.data.Badges
import app.leestn.data.Dates
import app.leestn.data.Podcast
import app.leestn.state.AppViewModel
import app.leestn.state.Load
import app.leestn.ui.theme.LColors
import java.util.Locale

/** Another person on your server: their stats, badges, favourite shows and latest listens. */
@Composable
fun FriendScreen(
    vm: AppViewModel,
    handle: String,
    onBack: () -> Unit,
    /** epId (may be empty), showId, show name, artwork, feed address */
    openRemote: (String, String, String, String, String) -> Unit,
) {
    LaunchedEffect(handle) { vm.loadProfile(handle, force = true) }
    val pr = vm.profiles[handle]
    val load = vm.profileLoad[handle]

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
        if (pr == null) {
            item {
                if (load is Load.Failed) ErrorState(load.message, onRetry = { vm.loadProfile(handle, force = true) })
                else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBox(Modifier.width(76.dp).padding(0.dp), 38.dp)
                    SkeletonBox(Modifier.fillMaxWidth(0.5f), 6.dp)
                }
            }
            return@LazyColumn
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(vm, pr.handle, pr.name, pr.avatarV, 76.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(pr.name.ifBlank { pr.handle }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false))
                            FounderBadge(pr.role, size = 24.dp)
                        }
                        FounderTag(pr.role)
                        Text("@${pr.handle}  ·  ${pr.followers} follower${if (pr.followers == 1) "" else "s"}  ·  ${pr.following} following", style = MaterialTheme.typography.labelMedium, color = LColors.Text3)
                        if (pr.bio.isNotBlank()) Text(pr.bio, style = MaterialTheme.typography.bodyMedium, color = LColors.Text2)
                    }
                }
                if (!pr.isMe) Row(Modifier.padding(horizontal = 16.dp)) {
                    Chip(if (pr.isFollowing) "Following" else "Follow", !pr.isFollowing) { vm.toggleFollow(pr.handle) }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorBlock("Episodes", "${pr.episodes}", LColors.Lilac, Modifier.weight(1f))
                ColorBlock("Hours", String.format(Locale.US, "%.1f", pr.hours), LColors.Mint, Modifier.weight(1f))
                ColorBlock("Avg rating", if (pr.avgRating != null) String.format(Locale.US, "%.1f", pr.avgRating) else "–", LColors.Sky, Modifier.weight(1f))
            }
        }
        if (pr.badges.isNotEmpty()) item {
            val all = Badges.defs.map { d ->
                val tier = pr.badges.firstOrNull { it.id == d.id }?.tier ?: 0
                BadgeProgress(d, if (tier > 0) d.goals[(tier - 1).coerceAtMost(d.goals.size - 1)] else 0)
            }
            Panel("Badges", "${all.count { it.earned }} of ${all.size}") { BadgeStrip(all) }
        }
        if (pr.topShows.isNotEmpty()) item {
            Panel("Most listened shows") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pr.topShows.take(6).forEachIndexed { rank, sh ->
                        val stub = Podcast(sh.showId, sh.showName.ifBlank { "Podcast" }, "", "", "", sh.feedUrl, sh.showArt)
                        Row(
                            Modifier.fillMaxWidth().clickable { openRemote("", sh.showId, sh.showName, sh.showArt, sh.feedUrl) },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${rank + 1}", style = MaterialTheme.typography.titleLarge, color = if (rank == 0) LColors.Accent else LColors.Text3, modifier = Modifier.width(24.dp))
                            Poster(stub, Modifier.width(44.dp), corner = 12.dp)
                            Column(Modifier.weight(1f)) {
                                Text(sh.showName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${sh.episodes} episode${if (sh.episodes == 1) "" else "s"} · ${Dates.hours(sh.minutes)}", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
                            }
                            sh.avgRating?.let { RatingChip(it) }
                        }
                    }
                }
            }
        }
        if (pr.showRatings.isNotEmpty()) item {
            Panel("Rated shows") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pr.showRatings.forEach { sr ->
                        val stub = Podcast(sr.showId, sr.showName.ifBlank { "Podcast" }, "", "", "", "", sr.showArt)
                        Row(
                            Modifier.fillMaxWidth().clickable { openRemote("", sr.showId, sr.showName, sr.showArt, "") },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Poster(stub, Modifier.width(36.dp), corner = 12.dp)
                            Text(sr.showName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            RatingChip(sr.rating.toDouble())
                        }
                    }
                }
            }
        }
        item {
            Panel("Recent listens") {
                if (pr.recent.isEmpty()) Text("Nothing logged yet.", color = LColors.Text3)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pr.recent.forEach { r ->
                        val stub = Podcast(r.showId, r.showName.ifBlank { "Podcast" }, "", "", "", r.feedUrl, r.showArt)
                        Row(
                            Modifier.fillMaxWidth().clickable { openRemote(r.epId, r.showId, r.showName, r.showArt, r.feedUrl) },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Poster(stub, Modifier.width(36.dp), corner = 12.dp)
                            Column(Modifier.weight(1f)) {
                                Text(r.epTitle.ifBlank { "Episode" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.showName + (if (r.listenedAt.length >= 10) " · " + Dates.rel(r.listenedAt.substring(0, 10)) else ""), style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            RatingPill(r.rating)
                        }
                    }
                }
            }
        }
    }
}
