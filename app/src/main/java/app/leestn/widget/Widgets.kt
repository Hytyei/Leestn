package app.leestn.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.action.ActionParameters
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.leestn.MainActivity
import app.leestn.PlaybackService
import app.leestn.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---------- shared look ----------
// Flat, near-black surfaces, big artwork and one red accent, like the app.

private val RED = ColorProvider(Color(0xFFED1C24))
private val TEXT = ColorProvider(Color(0xFFF4F4F6))
private val DIM = ColorProvider(Color(0xFF8E8E99))
private val WHITE = ColorProvider(Color.White)
private val TRACK = ColorProvider(Color(0xFF26262E))

private fun mmss(sec: Int): String {
    val s = if (sec < 0) 0 else sec
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}

/** Opens the app on a screen, for example "ep:ID". The data URI keeps each PendingIntent distinct. */
private fun open(ctx: Context, route: String = "home"): Action {
    val i = Intent(ctx, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("leestn://open/" + Uri.encode(route))
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    return actionStartActivity(i)
}

@Composable
private fun Art(bmp: Bitmap?, size: Dp, corner: Dp = 14.dp) {
    Image(
        provider = if (bmp != null) ImageProvider(bmp) else ImageProvider(R.drawable.widget_art_fallback),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = GlanceModifier.size(size).cornerRadius(corner),
    )
}

@Composable
private fun Caps(text: String, color: ColorProvider = DIM) {
    Text(text.uppercase(), maxLines = 1, style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun RoundBtn(icon: Int, desc: String, size: Dp, primary: Boolean, action: Action) {
    Box(
        modifier = GlanceModifier.size(size)
            .background(ImageProvider(if (primary) R.drawable.widget_btn_primary else R.drawable.widget_btn))
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), desc, modifier = GlanceModifier.size(size * 0.52f))
    }
}

@Composable
private fun Pill(label: String, action: Action) {
    Box(
        GlanceModifier.background(ImageProvider(R.drawable.widget_pill)).padding(horizontal = 14.dp, vertical = 7.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = TextStyle(color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
}

@Composable
private fun Hairline() {
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(ImageProvider(R.drawable.widget_hairline))) {}
}

/** What a widget shows when nothing is playing: something worth starting instead of an empty box. */
private fun suggestion(s: WSnapshot): Pair<String, WItem>? =
    s.cont.firstOrNull()?.let { "Resume" to it } ?: s.fresh.firstOrNull()?.let { "New" to it }

// ---------- controlling the player from a widget ----------

private object Remote {
    /** Connects to the playback service, runs [block] on the main thread, then lets go. */
    suspend fun run(ctx: Context, block: (MediaController) -> Unit) {
        val app = ctx.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        val controller = suspendCancellableCoroutine<MediaController> { cont ->
            future.addListener({
                try { cont.resume(future.get()) } catch (e: Exception) { cont.resumeWithException(e) }
            }, ContextCompat.getMainExecutor(app))
        }
        try {
            withContext(Dispatchers.Main) { block(controller) }
            delay(400) // let the command reach the service before disconnecting
        } finally {
            withContext(Dispatchers.Main) { MediaController.releaseFuture(future) }
        }
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        var needsApp = false
        Remote.run(context) { c ->
            when {
                c.currentMediaItem == null -> needsApp = true
                c.playWhenReady && c.playbackState != androidx.media3.common.Player.STATE_ENDED -> c.pause()
                else -> c.play()
            }
        }
        if (needsApp) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class BackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Remote.run(context) { if (it.currentMediaItem != null) it.seekBack() }
    }
}

class ForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Remote.run(context) { if (it.currentMediaItem != null) it.seekForward() }
    }
}

private val playPause get() = actionRunCallback<PlayPauseAction>()
private val back15 get() = actionRunCallback<BackAction>()
private val fwd30 get() = actionRunCallback<ForwardAction>()

// ---------- 1. now playing (4x2) ----------
// Artwork takes the full height on the left. Title, progress and controls fill the rest.
// Nothing playing? It offers the episode you would most likely want next.

class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val now = snap.now
        val sug = if (now == null) suggestion(snap) else null
        val artUrl = now?.art ?: sug?.second?.art ?: ""
        val art = WidgetArt.bitmap(context, artUrl, 400)
        provideContent {
            val size = LocalSize.current
            val pad = 12.dp
            val artSize = if (size.height - pad * 2 < size.width * 0.42f) size.height - pad * 2 else size.width * 0.42f
            val tall = size.height >= 130.dp
            Box(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(pad)
                    .clickable(open(context, if (now != null) "ep:${now.id}" else if (sug != null) "ep:${sug.second.id}" else "discover")),
            ) {
                if (now == null && sug == null) {
                    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nothing to play yet", style = TextStyle(color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.height(8.dp))
                        Pill("Find a show", open(context, "discover"))
                    }
                } else {
                    Row(GlanceModifier.fillMaxSize()) {
                        Art(art, artSize)
                        Spacer(GlanceModifier.width(12.dp))
                        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                            if (now != null) {
                                Caps(now.show)
                                Text(now.title, maxLines = if (tall) 3 else 2, style = TextStyle(color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                Spacer(GlanceModifier.defaultWeight())
                                LinearProgressIndicator(progress = now.frac, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = RED, backgroundColor = TRACK)
                                Spacer(GlanceModifier.height(6.dp))
                                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (now.dur > 0) "-" + mmss(now.dur - now.pos) else mmss(now.pos),
                                        style = TextStyle(color = DIM, fontSize = 11.sp), modifier = GlanceModifier.defaultWeight(),
                                    )
                                    RoundBtn(R.drawable.ic_w_back15, "Back 15 seconds", 30.dp, false, back15)
                                    Spacer(GlanceModifier.width(6.dp))
                                    RoundBtn(if (now.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (now.playing) "Pause" else "Play", 38.dp, true, playPause)
                                    Spacer(GlanceModifier.width(6.dp))
                                    RoundBtn(R.drawable.ic_w_fwd30, "Forward 30 seconds", 30.dp, false, fwd30)
                                }
                            } else if (sug != null) {
                                Caps(if (sug.first == "Resume") "Pick up where you left off" else "New episode", RED)
                                Text(sug.second.title, maxLines = if (tall) 3 else 2, style = TextStyle(color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                Text(sug.second.show, maxLines = 1, style = TextStyle(color = DIM, fontSize = 11.sp))
                                Spacer(GlanceModifier.defaultWeight())
                                if (sug.second.frac > 0f) {
                                    LinearProgressIndicator(progress = sug.second.frac, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = RED, backgroundColor = TRACK)
                                    Spacer(GlanceModifier.height(6.dp))
                                }
                                Pill(if (sug.first == "Resume") "Resume" else "Listen", open(context, "ep:${sug.second.id}"))
                            }
                        }
                    }
                }
            }
        }
    }
}

class NowPlayingReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

// ---------- 2. mini player (2x2) ----------
// The cover is the whole widget. A dark fade at the bottom carries the title and the play button.

class MiniPlayerWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val now = snap.now
        val sug = if (now == null) suggestion(snap) else null
        val title = now?.title ?: sug?.second?.title ?: "Nothing playing"
        val show = now?.show ?: sug?.second?.show ?: ""
        val art = WidgetArt.bitmap(context, now?.art ?: sug?.second?.art ?: "", 500)
        provideContent {
            Box(
                GlanceModifier.fillMaxSize().clickable(open(context, if (now != null) "ep:${now.id}" else if (sug != null) "ep:${sug.second.id}" else "home")),
            ) {
                Image(
                    provider = if (art != null) ImageProvider(art) else ImageProvider(R.drawable.widget_art_fallback),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp),
                )
                Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_scrim))) {}
                Column(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                    if (now != null) LinearProgressIndicator(progress = now.frac, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = RED, backgroundColor = TRACK)
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(title, maxLines = 2, style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            if (show.isNotBlank()) Text(show, maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xFFD0D0D8)), fontSize = 10.sp))
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        RoundBtn(if (now?.playing == true) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (now?.playing == true) "Pause" else "Play", 40.dp, true, playPause)
                    }
                }
            }
        }
    }
}

class MiniPlayerReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniPlayerWidget()
}

// ---------- 3. continue listening (poster row, 4x2) ----------
// As many posters as fit the width, each with a progress bar. Falls back to new episodes.

class ContinueWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val resuming = snap.cont.isNotEmpty()
        val list = (if (resuming) snap.cont else snap.fresh).take(6)
        val art = list.associate { it.id to WidgetArt.bitmap(context, it.art, 300) }
        provideContent {
            val size = LocalSize.current
            val pad = 12.dp
            val gap = 8.dp
            // poster size from the height that is left after the header and the title line
            val raw = size.height - pad * 2 - 20.dp - 34.dp
            val poster = if (raw < 56.dp) 56.dp else if (raw > 120.dp) 120.dp else raw
            val fit = ((size.width - pad * 2 + gap).value / (poster + gap).value).toInt()
            val n = if (fit < 1) 1 else if (fit > 6) 6 else fit
            Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(pad)) {
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(open(context)), verticalAlignment = Alignment.CenterVertically) {
                    Caps(if (resuming) "Continue listening" else "New for you", RED)
                    Spacer(GlanceModifier.defaultWeight())
                    Caps("leestn")
                }
                if (list.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize().clickable(open(context, "discover")), contentAlignment = Alignment.Center) {
                        Text("Nothing in progress. Find a show.", style = TextStyle(color = DIM, fontSize = 12.sp))
                    }
                } else {
                    Row(GlanceModifier.fillMaxWidth()) {
                        list.take(n).forEachIndexed { i, item ->
                            if (i > 0) Spacer(GlanceModifier.width(gap))
                            Column(GlanceModifier.width(poster).clickable(open(context, "ep:${item.id}"))) {
                                Art(art[item.id], poster, 12.dp)
                                Spacer(GlanceModifier.height(5.dp))
                                if (resuming) {
                                    LinearProgressIndicator(progress = item.frac, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = RED, backgroundColor = TRACK)
                                    Spacer(GlanceModifier.height(4.dp))
                                }
                                Text(item.title, maxLines = 1, style = TextStyle(color = TEXT, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }
    }
}

class ContinueReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWidget()
}

// ---------- 4. new episodes (scrolling list, 4x3) ----------
// Flat rows with hairlines instead of boxed cards, so more fits in the same space.

class NewEpisodesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val list = snap.fresh.ifEmpty { snap.cont }
        val art = list.associate { it.id to WidgetArt.bitmap(context, it.art, 160) }
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).clickable(open(context)), verticalAlignment = Alignment.CenterVertically) {
                    Caps(if (snap.fresh.isNotEmpty()) "New episodes" else "In progress", RED)
                    Spacer(GlanceModifier.defaultWeight())
                    Caps("leestn")
                }
                if (list.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize().clickable(open(context, "discover")), contentAlignment = Alignment.Center) {
                        Text("Subscribe to shows to see new episodes", style = TextStyle(color = DIM, fontSize = 12.sp))
                    }
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(list, itemId = { it.id.hashCode().toLong() }) { item ->
                            Column(GlanceModifier.fillMaxWidth()) {
                                Row(
                                    GlanceModifier.fillMaxWidth().padding(vertical = 7.dp).clickable(open(context, "ep:${item.id}")),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Art(art[item.id], 46.dp, 10.dp)
                                    Spacer(GlanceModifier.width(10.dp))
                                    Column(GlanceModifier.defaultWeight()) {
                                        Text(item.title, maxLines = 2, style = TextStyle(color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                        Text(item.sub.ifBlank { item.show }, maxLines = 1, style = TextStyle(color = DIM, fontSize = 11.sp))
                                    }
                                }
                                Hairline()
                            }
                        }
                    }
                }
            }
        }
    }
}

class NewEpisodesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewEpisodesWidget()
}

// ---------- 5. stats (2x2 and wider) ----------
// Big hours number, a few lines of numbers, and a bar for each of the last seven days.

class StatsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val st = WidgetStore.read(context).stats
        provideContent {
            val size = LocalSize.current
            val wide = size.width >= 220.dp
            val chartH = if (size.height >= 150.dp) 46.dp else 28.dp
            val week = st?.week ?: List(7) { 0 }
            val peak = (week.maxOrNull() ?: 0).coerceAtLeast(1)
            val today = java.time.LocalDate.now()
            val letters = (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.name.first().toString() }
            Column(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(14.dp).clickable(open(context, "profile")),
            ) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Top) {
                    Column(GlanceModifier.defaultWeight()) {
                        Caps("${st?.year ?: ""} listening".trim(), RED)
                        Text(st?.hours ?: "0.0", style = TextStyle(color = TEXT, fontSize = if (wide) 34.sp else 30.sp, fontWeight = FontWeight.Bold))
                        Text("hours", style = TextStyle(color = DIM, fontSize = 11.sp))
                    }
                    if (wide) {
                        Column(GlanceModifier.defaultWeight()) {
                            StatLine("${st?.episodes ?: 0}", "episodes")
                            StatLine("${st?.streak ?: 0}", "day streak")
                            if (st != null && st.avg.isNotBlank()) StatLine(st.avg, "avg rating")
                        }
                    }
                }
                if (!wide) {
                    Text("${st?.episodes ?: 0} eps · ${st?.streak ?: 0} day streak", maxLines = 1, style = TextStyle(color = DIM, fontSize = 10.sp))
                    Spacer(GlanceModifier.height(6.dp))
                }
                Row(GlanceModifier.fillMaxWidth().height(chartH), verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { i, v ->
                        if (i > 0) Spacer(GlanceModifier.width(5.dp))
                        val h = if (v <= 0) 4.dp else (chartH.value * v / peak).coerceAtLeast(4f).dp
                        Box(
                            GlanceModifier.defaultWeight().height(h)
                                .background(ImageProvider(if (v > 0) R.drawable.widget_bar else R.drawable.widget_bar_empty)),
                        ) {}
                    }
                }
                Spacer(GlanceModifier.height(3.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    letters.forEachIndexed { i, l ->
                        if (i > 0) Spacer(GlanceModifier.width(5.dp))
                        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            Text(l, style = TextStyle(color = DIM, fontSize = 9.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = TextStyle(color = TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(4.dp))
        Text(label, style = TextStyle(color = DIM, fontSize = 11.sp), modifier = GlanceModifier.padding(bottom = 2.dp))
    }
}

class StatsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()
}
