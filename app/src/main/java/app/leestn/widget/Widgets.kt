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
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
// Near-black surfaces tinted with each cover's colour, big rounded artwork and one red accent, like the app.
// Gradients, rounded images, progress bars and charts are painted into bitmaps by WidgetArt (WidgetStore.kt).

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

private fun col(argb: Int) = ColorProvider(Color(argb))

/** The cover's colour lightened a little, for small text on the dark card. */
private fun soft(tint: Int) = col(WidgetArt.mix(tint, android.graphics.Color.WHITE, 0.22f))

/** A bitmap stretched to its box. The bitmaps are painted at the box's own proportions, so nothing distorts. */
@Composable
private fun Pic(bmp: Bitmap, modifier: GlanceModifier) {
    Image(provider = ImageProvider(bmp), contentDescription = null, modifier = modifier, contentScale = ContentScale.FillBounds)
}

@Composable
private fun Art(bmp: Bitmap, size: Dp) = Pic(bmp, GlanceModifier.size(size))

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
private fun GlassBtn(icon: Int, desc: String, size: Dp, action: Action) {
    Box(
        modifier = GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_btn_glass)).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), desc, modifier = GlanceModifier.size(size * 0.5f))
    }
}

@Composable
private fun Pill(label: String, action: Action) {
    Box(
        GlanceModifier.background(ImageProvider(R.drawable.widget_pill)).padding(horizontal = 14.dp, vertical = 7.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = TextStyle(color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
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
// Rounded cover on the left, the whole card washed with the cover's colour. Pill progress and clear controls.
// Nothing playing? It offers the episode you would most likely want next.

class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val now = snap.now
        val sug = if (now == null) suggestion(snap) else null
        val artUrl = now?.art ?: sug?.second?.art ?: ""
        val art = WidgetArt.bitmap(context, artUrl, 400)
        val tint = WidgetArt.tint(art)
        val d = context.resources.displayMetrics.density
        provideContent {
            val size = LocalSize.current
            val pad = 12.dp
            val artSize = if (size.height - pad * 2 < size.width * 0.42f) size.height - pad * 2 else size.width * 0.42f
            val tall = size.height >= 130.dp
            val colRaw = size.width - pad * 2 - artSize - 12.dp
            val colW = if (colRaw < 40.dp) 40.dp else colRaw
            val bg = WidgetArt.backdrop(size.width.value, size.height.value, 24f, tint, d)
            val cover = WidgetArt.roundedCrop(art, artSize.value, artSize.value, 18f, d, tint)
            val frac = now?.frac ?: sug?.second?.frac ?: 0f
            val pill = WidgetArt.progressPill(colW.value, 12f, frac, tint, d)
            Box(
                GlanceModifier.fillMaxSize()
                    .clickable(open(context, if (now != null) "ep:${now.id}" else if (sug != null) "ep:${sug.second.id}" else "discover")),
            ) {
                Pic(bg, GlanceModifier.fillMaxSize())
                Box(GlanceModifier.fillMaxSize().padding(pad)) {
                    if (now == null && sug == null) {
                        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nothing to play yet", style = TextStyle(color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                            Spacer(GlanceModifier.height(8.dp))
                            Pill("Find a show", open(context, "discover"))
                        }
                    } else {
                        Row(GlanceModifier.fillMaxSize()) {
                            Art(cover, artSize)
                            Spacer(GlanceModifier.width(12.dp))
                            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                                if (now != null) {
                                    Caps(if (now.playing) "Playing · ${now.show}" else now.show, soft(tint))
                                    Text(now.title, maxLines = if (tall) 3 else 2, style = TextStyle(color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                    Spacer(GlanceModifier.defaultWeight())
                                    Pic(pill, GlanceModifier.fillMaxWidth().height(12.dp))
                                    Spacer(GlanceModifier.height(4.dp))
                                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (now.dur > 0) "-" + mmss(now.dur - now.pos) else mmss(now.pos),
                                            style = TextStyle(color = DIM, fontSize = 11.sp), modifier = GlanceModifier.defaultWeight(),
                                        )
                                        RoundBtn(R.drawable.ic_w_back15, "Back 15 seconds", 30.dp, false, back15)
                                        Spacer(GlanceModifier.width(6.dp))
                                        RoundBtn(if (now.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (now.playing) "Pause" else "Play", 40.dp, true, playPause)
                                        Spacer(GlanceModifier.width(6.dp))
                                        RoundBtn(R.drawable.ic_w_fwd30, "Forward 30 seconds", 30.dp, false, fwd30)
                                    }
                                } else if (sug != null) {
                                    Caps(if (sug.first == "Resume") "Pick up where you left off" else "New episode", soft(tint))
                                    Text(sug.second.title, maxLines = if (tall) 3 else 2, style = TextStyle(color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                    Text(sug.second.show, maxLines = 1, style = TextStyle(color = DIM, fontSize = 11.sp))
                                    Spacer(GlanceModifier.defaultWeight())
                                    if (sug.second.frac > 0f) {
                                        Pic(pill, GlanceModifier.fillMaxWidth().height(12.dp))
                                        Spacer(GlanceModifier.height(4.dp))
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
}

class NowPlayingReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

// ---------- 2. mini player (2x2) ----------
// The cover is the whole widget. A fade in the cover's colour carries the title, a pill progress bar and a glass play button.

class MiniPlayerWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val now = snap.now
        val sug = if (now == null) suggestion(snap) else null
        val title = now?.title ?: sug?.second?.title ?: "Nothing playing"
        val show = now?.show ?: sug?.second?.show ?: ""
        val art = WidgetArt.bitmap(context, now?.art ?: sug?.second?.art ?: "", 500)
        val tint = WidgetArt.tint(art)
        val d = context.resources.displayMetrics.density
        provideContent {
            val size = LocalSize.current
            val full = WidgetArt.roundedCrop(art, size.width.value, size.height.value, 24f, d, tint)
            val scrim = WidgetArt.scrim(size.width.value, size.height.value, 24f, tint, d)
            val barW = if (size.width - 24.dp < 20.dp) 20.dp else size.width - 24.dp
            val pill = WidgetArt.progressPill(barW.value, 10f, now?.frac ?: 0f, tint, d)
            Box(
                GlanceModifier.fillMaxSize().clickable(open(context, if (now != null) "ep:${now.id}" else if (sug != null) "ep:${sug.second.id}" else "home")),
            ) {
                Pic(full, GlanceModifier.fillMaxSize())
                Pic(scrim, GlanceModifier.fillMaxSize())
                Column(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                    if (now != null) {
                        Pic(pill, GlanceModifier.fillMaxWidth().height(10.dp))
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(title, maxLines = 2, style = TextStyle(color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            if (show.isNotBlank()) Text(show, maxLines = 1, style = TextStyle(color = soft(tint), fontSize = 10.sp, fontWeight = FontWeight.Medium))
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        GlassBtn(if (now?.playing == true) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (now?.playing == true) "Pause" else "Play", 42.dp, playPause)
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
// As many rounded posters as fit the width, each with a pill progress bar and how much is left. Falls back to new episodes.

class ContinueWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val resuming = snap.cont.isNotEmpty()
        val list = (if (resuming) snap.cont else snap.fresh).take(6)
        val art = list.associate { it.id to WidgetArt.bitmap(context, it.art, 300) }
        val tints = list.associate { it.id to WidgetArt.tint(art[it.id]) }
        val base = list.firstOrNull()?.let { tints[it.id] } ?: WidgetArt.RED
        val d = context.resources.displayMetrics.density
        provideContent {
            val size = LocalSize.current
            val pad = 12.dp
            val gap = 8.dp
            // poster size from the height that is left after the header, the progress bar and two text lines
            val raw = size.height - pad * 2 - 20.dp - (if (resuming) 50.dp else 36.dp)
            val poster = if (raw < 56.dp) 56.dp else if (raw > 120.dp) 120.dp else raw
            val fit = ((size.width - pad * 2 + gap).value / (poster + gap).value).toInt()
            val n = if (fit < 1) 1 else if (fit > 6) 6 else fit
            val bg = WidgetArt.backdrop(size.width.value, size.height.value, 24f, base, d, 0.3f)
            Box(GlanceModifier.fillMaxSize()) {
                Pic(bg, GlanceModifier.fillMaxSize())
                Column(GlanceModifier.fillMaxSize().padding(pad)) {
                    Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(open(context)), verticalAlignment = Alignment.CenterVertically) {
                        Caps(if (resuming) "Continue listening" else "New for you", soft(base))
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
                                val tint = tints.getValue(item.id)
                                val cover = WidgetArt.roundedCrop(art[item.id], poster.value, poster.value, 14f, d, tint)
                                Column(GlanceModifier.width(poster).clickable(open(context, "ep:${item.id}"))) {
                                    Art(cover, poster)
                                    Spacer(GlanceModifier.height(4.dp))
                                    if (resuming) {
                                        Pic(WidgetArt.progressPill(poster.value, 10f, item.frac, tint, d), GlanceModifier.fillMaxWidth().height(10.dp))
                                    }
                                    Text(item.title, maxLines = 1, style = TextStyle(color = TEXT, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                                    if (resuming && item.frac > 0f) {
                                        val left = Math.round((1f - item.frac) * 100f)
                                        Text("$left% left", maxLines = 1, style = TextStyle(color = soft(tint), fontSize = 10.sp))
                                    }
                                }
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
// Rounded cards with the cover, the show name in the cover's colour and a red dot on what is new.

class NewEpisodesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        val isNew = snap.fresh.isNotEmpty()
        val list = snap.fresh.ifEmpty { snap.cont }
        val d = context.resources.displayMetrics.density
        val raw = list.associate { it.id to WidgetArt.bitmap(context, it.art, 160) }
        val tints = list.associate { it.id to WidgetArt.tint(raw[it.id]) }
        val covers = list.associate { it.id to WidgetArt.roundedCrop(raw[it.id], 52f, 52f, 12f, d, tints.getValue(it.id)) }
        val base = list.firstOrNull()?.let { tints[it.id] } ?: WidgetArt.RED
        provideContent {
            val size = LocalSize.current
            val bg = WidgetArt.backdrop(size.width.value, size.height.value, 24f, base, d, 0.3f)
            Box(GlanceModifier.fillMaxSize()) {
                Pic(bg, GlanceModifier.fillMaxSize())
                Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                    Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(open(context)), verticalAlignment = Alignment.CenterVertically) {
                        Caps(if (isNew) "New episodes" else "In progress", soft(base))
                        if (isNew) {
                            Spacer(GlanceModifier.width(6.dp))
                            Box(GlanceModifier.background(ImageProvider(R.drawable.widget_chip)).padding(horizontal = 7.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
                                Text("${snap.fresh.size}", style = TextStyle(color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                            }
                        }
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
                                        GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_card)).padding(8.dp)
                                            .clickable(open(context, "ep:${item.id}")),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Art(covers.getValue(item.id), 52.dp)
                                        Spacer(GlanceModifier.width(10.dp))
                                        Column(GlanceModifier.defaultWeight()) {
                                            Text(item.show.ifBlank { "Podcast" }, maxLines = 1, style = TextStyle(color = soft(tints.getValue(item.id)), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                            Text(item.title, maxLines = 2, style = TextStyle(color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                            if (item.sub.isNotBlank()) Text(item.sub, maxLines = 1, style = TextStyle(color = DIM, fontSize = 10.sp))
                                        }
                                        if (isNew) {
                                            Spacer(GlanceModifier.width(8.dp))
                                            Box(GlanceModifier.size(8.dp).background(ImageProvider(R.drawable.widget_dot))) {}
                                        }
                                    }
                                    Spacer(GlanceModifier.height(6.dp))
                                }
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
// A ring with one segment per day of the last week, the big hours number, and rounded gradient bars with today lit.

class StatsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val st = WidgetStore.read(context).stats
        val d = context.resources.displayMetrics.density
        provideContent {
            val size = LocalSize.current
            val wide = size.width >= 220.dp
            val chartH = if (size.height >= 150.dp) 46.dp else 28.dp
            val week = st?.week ?: List(7) { 0 }
            val lit = week.map { it > 0 }
            val litCount = lit.count { it }
            val today = java.time.LocalDate.now()
            val letters = (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.name.first().toString() }
            val inset = 14.dp
            val chartW = if (size.width - inset * 2 < 40.dp) 40.dp else size.width - inset * 2
            val ringSize = if (wide) 56.dp else 46.dp
            val bg = WidgetArt.backdrop(size.width.value, size.height.value, 24f, WidgetArt.RED, d, 0.22f)
            val chart = WidgetArt.weekChart(chartW.value, chartH.value, week, WidgetArt.RED, 5f, d)
            val ring = WidgetArt.ring(ringSize.value, lit, WidgetArt.RED, d)
            Box(GlanceModifier.fillMaxSize().clickable(open(context, "profile"))) {
                Pic(bg, GlanceModifier.fillMaxSize())
                Column(GlanceModifier.fillMaxSize().padding(inset)) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Top) {
                        Column(GlanceModifier.defaultWeight()) {
                            Caps("${st?.year ?: ""} listening".trim(), RED)
                            Text(st?.hours ?: "0.0", style = TextStyle(color = TEXT, fontSize = if (wide) 34.sp else 30.sp, fontWeight = FontWeight.Bold))
                            Text("hours", style = TextStyle(color = DIM, fontSize = 11.sp))
                        }
                        Box(GlanceModifier.size(ringSize), contentAlignment = Alignment.Center) {
                            Pic(ring, GlanceModifier.fillMaxSize())
                            Text("$litCount/7", style = TextStyle(color = TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                        if (wide) {
                            Spacer(GlanceModifier.width(14.dp))
                            Column {
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
                    Pic(chart, GlanceModifier.fillMaxWidth().height(chartH))
                    Spacer(GlanceModifier.height(3.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        letters.forEachIndexed { i, l ->
                            if (i > 0) Spacer(GlanceModifier.width(5.dp))
                            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                                Text(l, style = TextStyle(color = if (i == letters.size - 1) RED else DIM, fontSize = 9.sp, fontWeight = if (i == letters.size - 1) FontWeight.Bold else FontWeight.Normal))
                            }
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
