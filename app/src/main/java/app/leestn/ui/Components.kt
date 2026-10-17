package app.leestn.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.leestn.data.Catalog
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.leestn.data.Dates
import app.leestn.data.Episode
import app.leestn.data.Kind
import app.leestn.data.ListItem
import app.leestn.data.Motif
import app.leestn.data.Server
import app.leestn.data.Podcast
import app.leestn.state.AppViewModel
import app.leestn.ui.theme.LColors

// ---------- posters ----------

private fun DrawScope.drawMotif(m: Motif) {
    val s = size.width / 100f
    val oy = (size.height - 100f * s) * 0.4f
    val w = Color.White
    translate(left = 0f, top = oy) {
        when (m) {
            Motif.ARCS -> listOf(14f, 28f, 42f, 56f, 70f).forEach {
                drawCircle(w.copy(alpha = 0.4f), radius = it * s, center = Offset(18f * s, 82f * s), style = Stroke(1.2f * s))
            }
            Motif.BARS -> {
                val rows = listOf(Triple(20f, 62f, 0.28f), Triple(34f, 44f, 0.28f), Triple(48f, 76f, 0.6f), Triple(62f, 30f, 0.28f))
                rows.forEach { (y, ww, a) ->
                    drawRoundRect(w.copy(alpha = a), Offset(12f * s, y * s), Size(ww * s, 7f * s), CornerRadius(3.5f * s))
                }
            }
            Motif.MOON -> {
                drawCircle(w.copy(alpha = 0.18f), radius = 20f * s, center = Offset(62f * s, 38f * s))
                drawCircle(w.copy(alpha = 0.5f), radius = 20f * s, center = Offset(62f * s, 38f * s), style = Stroke(1.2f * s))
                listOf(Triple(24f, 22f, 1.4f), Triple(36f, 60f, 1f), Triple(82f, 72f, 1.5f), Triple(14f, 48f, 1f)).forEach { (x, y, r) ->
                    drawCircle(w.copy(alpha = 0.65f), radius = r * s, center = Offset(x * s, y * s))
                }
            }
            Motif.BEAM -> {
                val path = Path().apply {
                    moveTo(50f * s, -5f * s); lineTo(18f * s, 100f * s); lineTo(82f * s, 100f * s); close()
                }
                drawPath(path, w.copy(alpha = 0.14f))
                drawOval(w.copy(alpha = 0.2f), Offset(20f * s, 82f * s), Size(60f * s, 12f * s))
            }
            Motif.WAVES -> listOf(38f, 52f, 66f, 80f).forEach { y ->
                val path = Path().apply {
                    moveTo(-5f * s, y * s)
                    quadraticTo(12f * s, (y - 12f) * s, 30f * s, y * s)
                    quadraticTo(48f * s, (y + 12f) * s, 65f * s, y * s)
                    quadraticTo(82f * s, (y - 12f) * s, 105f * s, y * s)
                }
                drawPath(path, w.copy(alpha = 0.4f), style = Stroke(1.3f * s))
            }
            Motif.PIXELS -> {
                val pat = listOf("0111110", "1101011", "1111111", "1011101", "0010100")
                pat.forEachIndexed { y, row ->
                    row.forEachIndexed { x, c ->
                        if (c == '1') drawRoundRect(w.copy(alpha = 0.5f), Offset((18f + x * 9f) * s, (26f + y * 9f) * s), Size(8f * s, 8f * s), CornerRadius(1.5f * s))
                    }
                }
            }
        }
    }
}

/** Show artwork from the feed, over a generated gradient that also serves as the loading and fallback state. Square like podcast covers. */
@Composable
fun Poster(
    p: Podcast,
    modifier: Modifier = Modifier,
    ratio: Float = 1f,
    corner: Dp = 12.dp,
    showName: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    val brush = Brush.linearGradient(listOf(Color.hsl(p.hueA, 0.62f, 0.42f), Color.hsl(p.hueB, 0.58f, 0.14f)))
    val hasArt = p.artworkUrl.isNotBlank()
    Box(
        modifier
            .aspectRatio(ratio)
            .clip(shape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) { drawMotif(p.motif) }
        if (hasArt) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(p.artworkUrl).crossfade(350).build(),
                contentDescription = p.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showName) {
            Text(
                p.name,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A show that may not be in the catalogue yet (for example a saved item before its feed is restored). */
fun showOf(pid: String): Podcast = Catalog.show(pid) ?: Podcast(pid, "Podcast", "", "", "")

/** Round profile picture over a colour gradient with initials, so people without a photo still look distinct. */
@Composable
fun Avatar(name: String, seed: String, model: Any?, size: Dp = 36.dp) {
    val h = (Math.abs(seed.hashCode().toLong()) % 360L).toFloat()
    val brush = Brush.linearGradient(listOf(Color.hsl(h, 0.65f, 0.55f), Color.hsl((h + 50f) % 360f, 0.6f, 0.3f)))
    val initials = name.trim().split(" ").mapNotNull { w -> w.firstOrNull { c -> c.isLetterOrDigit() } }.take(2).joinToString("").uppercase().ifEmpty { "?" }
    Box(Modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun MyAvatar(vm: AppViewModel, size: Dp = 36.dp) {
    Avatar(vm.displayName, vm.serverHandle.ifBlank { "me" }, vm.avatarFile, size)
}

@Composable
fun PersonAvatar(vm: AppViewModel, handle: String, name: String, avatarV: Long, size: Dp = 36.dp) {
    val model = if (avatarV > 0L && vm.serverUrl.isNotBlank()) Server.avatarUrl(vm.serverUrl, handle, avatarV) else null
    Avatar(name, handle, model, size)
}

// ---------- small pieces ----------

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = LColors.Red, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
        }
    }
}

@Composable
fun RatingPill(r: Int?) {
    if (r == null) return
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(LColors.RedSoft).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("$r", color = LColors.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("/10", color = LColors.Red.copy(alpha = 0.7f), fontSize = 9.sp, modifier = Modifier.padding(bottom = 1.dp))
    }
}

@Composable
fun KindTag(kind: Kind) {
    val main = kind == Kind.MAIN
    Text(
        if (main) "MAIN STORY" else "SIDE CONTENT",
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (main) LColors.RedSoft else LColors.SideSoft).padding(horizontal = 8.dp, vertical = 3.dp),
        color = if (main) LColors.Red else LColors.Side,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun CodeChip(ep: Episode) {
    val side = ep.kind == Kind.SIDE
    Box(
        Modifier.width(48.dp).clip(RoundedCornerShape(8.dp)).background(if (side) LColors.SideSoft else LColors.RedSoft).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(ep.code, color = if (side) LColors.Side else LColors.Red, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(99.dp)
    val bg by animateColorAsState(if (selected) LColors.Red else Color.Transparent, tween(Motion.Quick), label = "chipBg")
    val line by animateColorAsState(if (selected) LColors.Red else LColors.Surface3, tween(Motion.Quick), label = "chipLine")
    val fg by animateColorAsState(if (selected) Color.White else LColors.Text2, tween(Motion.Quick), label = "chipFg")
    Text(
        label,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, line), shape)
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun PlayButton(playing: Boolean, modifier: Modifier = Modifier, size: Dp = 36.dp, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(if (filled) LColors.Red else LColors.Surface3).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(playing, animationSpec = tween(Motion.Quick), label = "playIcon") { on ->
            Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (on) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(LColors.Surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
    }
}

@Composable
fun CountTile(value: Int, label: String, modifier: Modifier = Modifier) = StatTile(animatedInt(value).toString(), label, modifier)

// ---------- rows ----------

/** Episode row with status: logged date and rating, progress, or wishlist marker. */
@Composable
fun EpisodeRow(vm: AppViewModel, ep: Episode, onOpen: () -> Unit, ctx: String? = null, modifier: Modifier = Modifier) {
    val entry = vm.log[ep.id]
    val prog = vm.prog[ep.id]
    val isPlaying = vm.playing && vm.playingId == ep.id
    Row(
        modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 11.dp).animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CodeChip(ep)
        Column(Modifier.weight(1f)) {
            Text(ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ep.blurb, style = MaterialTheme.typography.bodySmall, color = LColors.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ctx != null) Text(ctx, style = MaterialTheme.typography.labelSmall, color = LColors.Side, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            when {
                entry?.d != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RatingPill(entry.r)
                    Icon(Icons.Rounded.Check, null, tint = LColors.Good, modifier = Modifier.size(14.dp))
                }
                prog != null -> Text("${Dates.clock(prog)} in", color = LColors.Side, style = MaterialTheme.typography.labelSmall)
                vm.inWishlist(ListItem.Ep(ep.id)) -> Icon(Icons.Rounded.Bookmark, "In wishlist", tint = LColors.Red, modifier = Modifier.size(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (app.leestn.data.Downloads.isDone(ep.id)) Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = LColors.Text3, modifier = Modifier.size(13.dp))
                Text(Dates.dur(ep.min), style = MaterialTheme.typography.labelSmall, color = LColors.Text3)
            }
        }
        PlayButton(isPlaying, onClick = { if (isPlaying) vm.togglePlay() else vm.play(ep.id) })
    }
}

@Composable
fun NextUpItem(vm: AppViewModel, ep: Episode, main: Boolean, hint: String, onOpen: () -> Unit) {
    val p = showOf(ep.pid)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LColors.Surface2).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Poster(p, Modifier.width(44.dp), corner = 8.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            KindTag(if (main) Kind.MAIN else Kind.SIDE)
            Text("${ep.code} · ${ep.title}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$hint · ${Dates.dur(ep.min)}", style = MaterialTheme.typography.bodySmall, color = LColors.Text3)
        }
        PlayButton(vm.playing && vm.playingId == ep.id, filled = true, onClick = { vm.play(ep.id) })
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))


// ---------- states ----------

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp).appear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LColors.Text3, textAlign = TextAlign.Center)
        if (action != null) { Gap(4.dp); action() }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp).appear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = LColors.Text3, modifier = Modifier.size(28.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = LColors.Text2, textAlign = TextAlign.Center)
        Chip("Try again", false, onRetry)
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LColors.Red, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
    }
}

/** Placeholder for a PosterCard while charts or search results load. */
@Composable
fun PosterCardSkeleton(modifier: Modifier = Modifier.width(124.dp)) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
        SkeletonBox(Modifier.fillMaxWidth(0.8f).height(12.dp), 4.dp)
        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(10.dp), 4.dp)
    }
}
