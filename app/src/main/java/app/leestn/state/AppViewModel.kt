package app.leestn.state

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.leestn.PlaybackService
import app.leestn.data.AvatarImage
import app.leestn.data.Backend
import app.leestn.data.GroupRatings
import app.leestn.data.RFeedItem
import app.leestn.data.RPerson
import app.leestn.data.RProfile
import app.leestn.data.ShareCode
import app.leestn.data.Server
import app.leestn.data.Usage
import app.leestn.data.Updater
import app.leestn.data.Version
import app.leestn.data.AppRelease
import app.leestn.data.UpdateState
import app.leestn.data.BadgeProgress
import app.leestn.data.Badges
import app.leestn.data.Catalog
import app.leestn.data.Kind
import app.leestn.data.Episode
import app.leestn.data.Downloads
import app.leestn.data.Genres
import app.leestn.data.Dl
import app.leestn.data.Dates
import app.leestn.data.Itunes
import app.leestn.data.ListItem
import app.leestn.data.LogEntry
import app.leestn.data.Podcast
import app.leestn.data.Sources
import app.leestn.data.StatsCalc
import app.leestn.widget.WItem
import app.leestn.widget.WNow
import app.leestn.widget.WSnapshot
import app.leestn.widget.WStats
import app.leestn.widget.WidgetStore
import app.leestn.data.TranscriptNet
import app.leestn.data.TxLine
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalTime

class UserList(val id: String, name: String, items: List<ListItem> = emptyList()) {
    var name by mutableStateOf(name)
    val items = mutableStateListOf<ListItem>().also { it.addAll(items) }
}

sealed class Load {
    object Idle : Load()
    object Busy : Load()
    object Done : Load()
    class Failed(val message: String) : Load()
}

sealed class TxState {
    object Loading : TxState()
    object None : TxState()
    class Failed(val message: String) : TxState()
    class Ready(val lines: List<TxLine>, val synced: Boolean, val generated: Boolean = false) : TxState()
    /** No transcript from the publisher, but you are connected to a server that can generate one on request. */
    class Offer(val note: String? = null) : TxState()
    class Generating(val progress: Float) : TxState()
}

/** What the player UI shows. Kept separately from the catalogue so it is available straight after a restart. */
data class NowMeta(val id: String, val pid: String, val title: String, val show: String, val art: String, val dur: Int)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("leestn_v2", Context.MODE_PRIVATE)

    // ---------- settings ----------
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set
    val genres = mutableStateListOf<String>().also { l ->
        prefs.getString("genres", "")?.split("|")?.filter { it.isNotBlank() }?.let { l.addAll(it) }
    }
    /** How many upcoming episodes to keep downloaded. 0 turns automatic downloads off. */
    var autoDl by mutableIntStateOf(prefs.getInt("autoDl", 0))
        private set
    var wifiOnly by mutableStateOf(prefs.getBoolean("wifiOnly", true))
        private set
    var deleteAfter by mutableStateOf(prefs.getBoolean("deleteAfter", false))
        private set

    // ---------- server and profile ----------
    /** Address of the Leestn server (a Cloudflare Worker) you belong to. Empty when not connected. */
    var serverUrl by mutableStateOf(prefs.getString("serverUrl", "") ?: "")
        private set
    /** Your account's secret on that server. */
    var serverToken by mutableStateOf(prefs.getString("serverToken", "") ?: "")
        private set
    /** The server's invite key. Whoever has it can share it, so friends can join. */
    var serverKey by mutableStateOf(prefs.getString("serverKey", "") ?: "")
        private set
    var serverHandle by mutableStateOf(prefs.getString("serverHandle", "") ?: "")
        private set
    var serverBusy by mutableStateOf<Load>(Load.Idle)
        private set

    // ---- app updates from GitHub releases ----
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var dismissedUpdate by mutableStateOf(prefs.getString("dismissedUpdate", "") ?: "")
        private set

    /** Looks for a newer release. Quiet unless [manual]. The automatic check runs at most every 6 hours. */
    fun checkUpdate(manual: Boolean) {
        val st = update
        if (st is UpdateState.Checking || st is UpdateState.Downloading || st is UpdateState.Ready) return
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("lastUpdateCheck", 0L) < 6 * 3600_000L) return
        update = UpdateState.Checking
        viewModelScope.launch {
            try {
                val rel = withContext(Dispatchers.IO) { Updater.latest() }
                prefs.edit().putLong("lastUpdateCheck", now).apply()
                val mine = Updater.installedVersion(app)
                update = if (rel != null && Version.isNewer(rel.version, mine)) UpdateState.Available(rel) else UpdateState.UpToDate
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                update = if (manual) UpdateState.Failed(e.message ?: "Couldn't check for updates") else UpdateState.Idle
            }
        }
    }

    fun dismissUpdate(version: String) {
        dismissedUpdate = version
        prefs.edit().putString("dismissedUpdate", version).apply()
    }

    /** Download the new APK, check it, then open Android's installer. */
    fun downloadUpdate(rel: AppRelease) {
        if (update is UpdateState.Downloading) return
        update = UpdateState.Downloading(rel, 0f)
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { Updater.download(app, rel) { p -> update = UpdateState.Downloading(rel, p) } }
                val bad = Updater.problem(app, f)
                if (bad != null) { f.delete(); update = UpdateState.Failed(bad); return@launch }
                update = UpdateState.Ready(rel, f)
                installUpdate()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                update = UpdateState.Failed(e.message ?: "The download failed")
            }
        }
    }

    /** Opens the installer for a downloaded update, asking for Android's permission first if needed. */
    fun installUpdate() {
        val st = update as? UpdateState.Ready ?: return
        if (!Updater.canInstall(app)) { Updater.openInstallPermission(app); return }
        Updater.install(app, st.file)
    }

    var usage by mutableStateOf<Usage?>(null)
        private set

    /** Refresh the free-Whisper meter. Never creates jobs or uses AI. */
    fun loadUsage() {
        if (!connected) { usage = null; return }
        val base = serverUrl
        val tok = serverToken
        viewModelScope.launch {
            try { usage = withContext(Dispatchers.IO) { Server.usage(base, tok) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Throwable) { /* keep the last value; the meter is optional */ }
        }
    }

    val connected: Boolean get() = serverUrl.startsWith("https://") && serverToken.isNotBlank()

    var profileName by mutableStateOf(prefs.getString("profileName", "") ?: "")
        private set
    var profileBio by mutableStateOf(prefs.getString("profileBio", "") ?: "")
        private set
    /** Changes whenever the profile photo does. 0 means no photo. */
    var avatarVer by mutableLongStateOf(prefs.getLong("avatarVer", 0L))
        private set
    val avatarFile: File? get() = if (avatarVer > 0L) File(app.filesDir, "avatar_$avatarVer.jpg").takeIf { it.exists() } else null
    val displayName: String get() = profileName.ifBlank { serverHandle.ifBlank { "You" } }

    var nowMeta by mutableStateOf<NowMeta?>(null)
        private set

    /** Set when a badge tier was just unlocked, so the UI can celebrate it. */
    var newBadge by mutableStateOf<BadgeProgress?>(null)
    private var pendingPlay: String? = null

    /** When playback should pause by itself, in epoch millis. 0 means no sleep timer. */
    var sleepEndsAt by mutableLongStateOf(0L)
        private set

    fun setSleep(minutes: Int) {
        sleepEndsAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
    }

    // ---------- user data ----------
    val log = mutableStateMapOf<String, LogEntry>()
    val prog = mutableStateMapOf<String, Int>()
    val lists = mutableStateListOf<UserList>()
    val subs = mutableStateListOf<String>()
    /** Your own rating (1 to 10) of whole shows. Episode ratings live in [log]. */
    val showRatings = mutableStateMapOf<String, Int>()
    private val stubs = LinkedHashMap<String, Podcast>()
    /** Episodes and shows whose changes haven't reached the server yet (id to change counter). */
    private val dirtyEps = HashMap<String, Int>().also { m -> prefs.getStringSet("dirtyEps", null)?.forEach { m[it] = 1 } }
    private val dirtyShows = HashMap<String, Int>().also { m -> prefs.getStringSet("dirtyShows", null)?.forEach { m[it] = 1 } }
    private var syncJob: Job? = null
    var profileError by mutableStateOf<String?>(null)

    // ---------- network state ----------
    var trending by mutableStateOf<List<Podcast>>(emptyList())
        private set
    var trendingLoad by mutableStateOf<Load>(Load.Idle)
        private set
    var results by mutableStateOf<List<Podcast>>(emptyList())
        private set
    var resultsLoad by mutableStateOf<Load>(Load.Idle)
        private set
    var resultsFor by mutableStateOf("")
        private set
    val showLoad = mutableStateMapOf<String, Load>()

    // friends, from the server
    var feedItems by mutableStateOf<List<RFeedItem>>(emptyList())
        private set
    var feedLoad by mutableStateOf<Load>(Load.Idle)
        private set
    var people by mutableStateOf<List<RPerson>>(emptyList())
        private set
    var peopleLoad by mutableStateOf<Load>(Load.Idle)
        private set
    val profiles = mutableStateMapOf<String, RProfile>()
    val profileLoad = mutableStateMapOf<String, Load>()
    /** What everyone on the server thinks of a show, by show id. */
    val group = mutableStateMapOf<String, GroupRatings>()
    val transcripts = mutableStateMapOf<String, TxState>()
    private val fetchedAt = HashMap<String, Long>()
    private var searchJob: Job? = null
    private val gate = Semaphore(4)

    var addLoad by mutableStateOf<Load>(Load.Idle)
        private set
    var addMessage by mutableStateOf<String?>(null)
        private set
    var addedId by mutableStateOf<String?>(null)

    // ---------- player state ----------
    var playingId by mutableStateOf<String?>(null)
        private set
    var t by mutableFloatStateOf(0f)
        private set
    var durationSec by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var finishedId by mutableStateOf<String?>(null)
    var playerError by mutableStateOf<String?>(null)

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var ticks = 0

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncState()
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
            if (playbackState == Player.STATE_ENDED) finish()
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            playerError = error.message ?: "Playback failed"
            playing = false
            buffering = false
        }
    }

    init {
        Catalog.init(app)
        speed = prefs.getFloat("speed", 1f)
        load()
        stubs.values.forEach { Catalog.register(it) }
        Downloads.init(app)
        prefs.getString("nowMeta", null)?.let { raw ->
            try {
                val o = JSONObject(raw)
                val m = NowMeta(o.getString("id"), o.getString("pid"), o.getString("title"), o.optString("show"), o.optString("art"), o.optInt("dur"))
                nowMeta = m
                playingId = m.id
                durationSec = m.dur
                t = (prog[m.id] ?: 0).toFloat()
            } catch (e: Exception) { /* ignore a corrupt value */ }
        }
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { Catalog.loadCached(stubs.keys.toList()) }
            cached.forEach { Catalog.register(it) }
            playingId?.let { id -> Catalog.episode(id)?.let { durationSec = it.durationSec } }
            if (deleteAfter) cleanupListened()
            checkBadges()
            if (onboarded) loadForYou()
            stubs.keys.toList().forEach { ensureShow(it) }
            publishWidgets()
            if (connected && (dirtyEps.isNotEmpty() || dirtyShows.isNotEmpty())) scheduleSync(1500)
        }
        connect()
        viewModelScope.launch {
            while (true) {
                delay(250)
                tick()
            }
        }
    }

    // ---------- persistence ----------
    private fun load() {
        val raw = prefs.getString("state", null)
        if (raw == null) {
            defaults()
            return
        }
        try {
            val j = JSONObject(raw)
            val lj = j.getJSONObject("log")
            lj.keys().forEach { k ->
                val o = lj.getJSONObject(k)
                log[k] = LogEntry(if (o.isNull("d")) null else o.getString("d"), if (o.isNull("r")) null else o.getInt("r"), o.optInt("h", 20))
            }
            val pj = j.getJSONObject("prog")
            pj.keys().forEach { k -> prog[k] = pj.getInt(k) }
            val ls = j.getJSONArray("lists")
            for (i in 0 until ls.length()) {
                val o = ls.getJSONObject(i)
                val items = ArrayList<ListItem>()
                val ia = o.getJSONArray("items")
                for (x in 0 until ia.length()) {
                    val it = ia.getJSONObject(x)
                    items.add(if (it.getString("t") == "e") ListItem.Ep(it.getString("id")) else ListItem.Show(it.getString("id")))
                }
                lists.add(UserList(o.getString("id"), o.getString("name"), items))
            }
            if (lists.none { it.id == "wishlist" }) lists.add(0, UserList("wishlist", "Wishlist"))
            val sa = j.getJSONArray("subs")
            for (i in 0 until sa.length()) subs.add(sa.getString(i))
            j.optJSONObject("srat")?.let { o -> o.keys().forEach { k -> showRatings[k] = o.getInt(k) } }
            val st = j.getJSONArray("stubs")
            for (i in 0 until st.length()) {
                val o = st.getJSONObject(i)
                val p = Podcast(
                    id = o.getString("id"), name = o.optString("name"), host = o.optString("host"), genre = o.optString("genre"),
                    tagline = o.optString("tagline"), feedUrl = o.optString("feed"), artworkUrl = o.optString("art"),
                )
                stubs[p.id] = p
            }
        } catch (e: Exception) {
            log.clear(); prog.clear(); lists.clear(); subs.clear(); showRatings.clear(); stubs.clear()
            defaults()
        }
    }

    private fun defaults() {
        lists.add(UserList("wishlist", "Wishlist"))
    }

    private fun save(widgets: Boolean = true) {
        val j = JSONObject()
        val lj = JSONObject()
        log.forEach { (k, v) ->
            lj.put(k, JSONObject().put("d", v.d ?: JSONObject.NULL).put("r", v.r ?: JSONObject.NULL).put("h", v.h))
        }
        j.put("log", lj)
        val pj = JSONObject()
        prog.forEach { (k, v) -> pj.put(k, v) }
        j.put("prog", pj)
        val ls = JSONArray()
        lists.forEach { l ->
            val ia = JSONArray()
            l.items.forEach { ia.put(JSONObject().put("t", if (it is ListItem.Ep) "e" else "p").put("id", it.id)) }
            ls.put(JSONObject().put("id", l.id).put("name", l.name).put("items", ia))
        }
        j.put("lists", ls)
        j.put("subs", JSONArray(subs.toList()))
        val sr = JSONObject()
        showRatings.forEach { (k, v) -> sr.put(k, v) }
        j.put("srat", sr)
        val st = JSONArray()
        stubs.values.forEach {
            st.put(
                JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("genre", it.genre)
                    .put("tagline", it.tagline).put("feed", it.feedUrl).put("art", it.artworkUrl),
            )
        }
        j.put("stubs", st)
        prefs.edit().putString("state", j.toString()).putFloat("speed", speed).putString("nowMeta", nowMeta?.let { m -> JSONObject().put("id", m.id).put("pid", m.pid).put("title", m.title).put("show", m.show).put("art", m.art).put("dur", m.dur).toString() }).apply()
        if (widgets) publishWidgets()
    }

    // ---------- home-screen widgets ----------
    private var widgetJob: Job? = null

    /** Debounced: many small changes in a row become one widget refresh. */
    private fun publishWidgets() {
        widgetJob?.cancel()
        widgetJob = viewModelScope.launch {
            delay(600)
            val snap = buildSnapshot()
            withContext(Dispatchers.Default) { WidgetStore.publish(app, snap) }
        }
    }

    private fun art(ep: Episode): String = ep.imageUrl.ifBlank { Catalog.show(ep.pid)?.artworkUrl ?: "" }

    private fun buildSnapshot(): WSnapshot {
        val now = playingId?.let { id ->
            Catalog.episode(id)?.let { ep ->
                WNow(ep.id, ep.title, Catalog.show(ep.pid)?.name ?: "", art(ep), playing, t.toInt(), if (durationSec > 0) durationSec else ep.durationSec)
            }
        }
        val cont = prog.entries.mapNotNull { (id, secs) ->
            val ep = Catalog.episode(id) ?: return@mapNotNull null
            val frac = if (ep.durationSec > 0) secs.toFloat() / ep.durationSec else 0f
            WItem(ep.id, ep.title, Catalog.show(ep.pid)?.name ?: "", art(ep), (Catalog.show(ep.pid)?.name ?: "") + " · " + Dates.clock(secs) + " in", frac)
        }.take(5)
        val fresh = subs.flatMap { pid -> Catalog.show(pid)?.eps?.take(6) ?: emptyList() }
            .filter { log[it.id]?.d == null && it.audioUrl.isNotBlank() }
            .sortedByDescending { it.date }
            .take(8)
            .map { ep -> WItem(ep.id, ep.title, Catalog.show(ep.pid)?.name ?: "", art(ep), (Catalog.show(ep.pid)?.name ?: "") + " · " + Dates.rel(ep.date), 0f) }
        val year = Dates.today().substring(0, 4)
        val st = StatsCalc.compute(log.toMap(), year)
        val week = IntArray(7)
        for ((id, l) in log) {
            val d = l.d ?: continue
            val ago = Dates.todayIdx() - Dates.idxOf(d)
            if (ago in 0..6) week[6 - ago] += Catalog.episode(id)?.min ?: 0
        }
        val stats = WStats(
            year, String.format(java.util.Locale.US, "%.1f", st.mins / 60.0), st.n, st.bestStreak,
            if (st.rated > 0) String.format(java.util.Locale.US, "%.1f", st.avg) else "",
            week = week.toList(),
        )
        return WSnapshot(now, cont, fresh, stats)
    }

    /** Keep enough about a show to restore it after a restart. */
    private fun remember(pid: String) {
        if (stubs.containsKey(pid)) return
        val p = Catalog.show(pid) ?: return
        if (p.feedUrl.isBlank()) return
        stubs[pid] = Podcast(p.id, p.name, p.host, p.genre, p.tagline, p.feedUrl, p.artworkUrl)
    }

    private fun rememberEp(id: String) {
        Catalog.episode(id)?.let { remember(it.pid) }
    }

    fun resetAll() {
        controller?.let { it.stop(); it.clearMediaItems() }
        playing = false; playingId = null; nowMeta = null; t = 0f; durationSec = 0; finishedId = null
        log.clear(); prog.clear(); lists.clear(); subs.clear(); showRatings.clear(); stubs.clear()
        dirtyEps.clear(); dirtyShows.clear()
        defaults()
        prefs.edit().remove("badges").apply()
        newBadge = null
        save()
    }

    // ---------- onboarding and settings ----------
    fun completeOnboarding(picked: List<String>) {
        genres.clear(); genres.addAll(picked)
        onboarded = true
        prefs.edit().putBoolean("onboarded", true).putString("genres", picked.joinToString("|")).apply()
        loadForYou(force = true)
    }

    fun setGenres(picked: List<String>) {
        genres.clear(); genres.addAll(picked)
        prefs.edit().putString("genres", picked.joinToString("|")).apply()
        loadForYou(force = true)
    }

    fun changeAutoDl(n: Int) {
        autoDl = n
        prefs.edit().putInt("autoDl", n).apply()
        playingId?.let { id -> Catalog.episode(id)?.let { autoDownloadAhead(it) } }
    }

    fun changeWifiOnly(on: Boolean) { wifiOnly = on; prefs.edit().putBoolean("wifiOnly", on).apply() }

    fun changeDeleteAfter(on: Boolean) {
        deleteAfter = on
        prefs.edit().putBoolean("deleteAfter", on).apply()
        if (on) cleanupListened()
    }


    // ---------- badges ----------
    fun computeBadges(entries: Map<String, LogEntry>): List<BadgeProgress> = Badges.compute(
        Badges.stats(
            entries,
            episode = { Catalog.episode(it) },
            genre = { Catalog.show(it)?.genre ?: "" },
            mainsOf = { Catalog.show(it)?.mains ?: emptyList() },
            today = Dates.today(),
        ),
    )

    /** Compare earned tiers with the ones already celebrated. The first run only records a baseline. */
    private fun checkBadges() {
        val earned = computeBadges(log.toMap()).filter { it.earned }
        val keys = earned.flatMap { b -> (1..b.tier).map { b.key(it) } }.toSet()
        val known = prefs.getStringSet("badges", null)?.toSet()
        if (known == null) {
            prefs.edit().putStringSet("badges", keys).apply()
            return
        }
        val fresh = keys - known
        if (fresh.isEmpty()) return
        newBadge = earned.filter { b -> (1..b.tier).any { b.key(it) in fresh } }.maxByOrNull { it.tier }
        prefs.edit().putStringSet("badges", known + keys).apply()
    }

    fun dismissBadge() { newBadge = null }

    // ---------- for you ----------
    val forYou = mutableStateMapOf<String, List<Podcast>>()
    var forYouLoad by mutableStateOf<Load>(Load.Idle)
        private set

    /** One search per favourite genre, in parallel. Shows you already follow are left out. */
    fun loadForYou(force: Boolean = false) {
        if (genres.isEmpty()) { forYou.clear(); forYouLoad = Load.Idle; return }
        if (forYouLoad is Load.Busy) return
        if (!force && forYou.isNotEmpty()) return
        forYouLoad = Load.Busy
        viewModelScope.launch {
            try {
                val picked = genres.toList().take(5)
                val got = withContext(Dispatchers.IO) {
                    picked.map { g -> async { g to try { Itunes.search(Genres.term(g), 20) } catch (e: Exception) { emptyList() } } }.awaitAll()
                }
                forYou.clear()
                got.forEach { (g, list) ->
                    val fresh = list.distinctBy { it.id }.filter { it.id !in subs }
                    fresh.forEach { Catalog.register(it) }
                    if (fresh.isNotEmpty()) forYou[g] = fresh
                }
                forYouLoad = if (forYou.isEmpty()) Load.Failed("Couldn't load suggestions") else Load.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                forYouLoad = Load.Failed(e.message ?: "Couldn't load suggestions")
            }
        }
    }

    // ---------- downloads ----------
    fun download(ep: Episode) {
        remember(ep.pid)
        Downloads.enqueue(app, ep, wifiOnly)
    }

    fun removeDownload(id: String) = Downloads.remove(app, id)

    /** Keep the next [autoDl] unlistened main episodes after [ep] downloaded. Safe to call repeatedly. */
    private fun autoDownloadAhead(ep: Episode) {
        val n = autoDl
        if (n <= 0) return
        val show = Catalog.show(ep.pid) ?: return
        val from = if (ep.kind == Kind.MAIN) show.mains.indexOfFirst { it.id == ep.id }
        else if (ep.afterId == null) -1 else show.mains.indexOfFirst { it.id == ep.afterId }
        show.mains.drop(from + 1).filter { log[it.id]?.d == null }.take(n).forEach { download(it) }
    }

    /** Remove offline copies of episodes you have already listened to. */
    fun cleanupListened() {
        Downloads.doneIds().filter { log[it]?.d != null && it != playingId }.forEach { Downloads.remove(app, it) }
    }

    // ---------- discovery ----------
    fun loadTrending(force: Boolean = false) {
        if (trendingLoad is Load.Busy) return
        if (!force && trending.isNotEmpty()) return
        trendingLoad = Load.Busy
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Itunes.top() }.distinctBy { it.id }
                list.forEach { Catalog.register(it) }
                trending = list
                trendingLoad = Load.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                trendingLoad = Load.Failed(e.message ?: "Couldn't load charts")
            }
        }
    }

    fun search(term: String) {
        searchJob?.cancel()
        val q = term.trim()
        if (q.length < 2) {
            results = emptyList(); resultsLoad = Load.Idle; resultsFor = ""
            return
        }
        resultsLoad = Load.Busy
        searchJob = viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Itunes.search(q) }.distinctBy { it.id }
                list.forEach { Catalog.register(it) }
                results = list
                resultsFor = q
                resultsLoad = Load.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // includes OutOfMemoryError from oversized responses: show an error instead of crashing
                resultsLoad = Load.Failed(e.message ?: "Search failed")
            }
        }
    }

    // ---------- shows ----------
    fun ensureShow(pid: String, force: Boolean = false) {
        val stub = Catalog.show(pid) ?: return
        if (showLoad[pid] is Load.Busy) return
        val fresh = System.currentTimeMillis() - (fetchedAt[pid] ?: 0L) < 30 * 60 * 1000L
        if (!force && stub.loaded && fresh) return
        if (stub.feedUrl.isBlank()) {
            showLoad[pid] = Load.Failed("This show has no feed")
            return
        }
        showLoad[pid] = Load.Busy
        viewModelScope.launch {
            try {
                val p = gate.withPermit { withContext(Dispatchers.IO) { Catalog.fetch(stub) } }
                Catalog.register(p)
                fetchedAt[pid] = System.currentTimeMillis()
                showLoad[pid] = Load.Done
                pendingPlay?.let { id -> if (Catalog.episode(id) != null) { pendingPlay = null; play(id) } }
            } catch (e: Exception) {
                // keep showing the cached copy when there is one
                showLoad[pid] = if (stub.loaded) Load.Done else Load.Failed(e.message ?: "Couldn't load this show")
            }
        }
    }

    /** Add a show from an RSS, Apple Podcasts or Spotify link. */
    fun addFromLink(text: String) {
        if (addLoad is Load.Busy) return
        addLoad = Load.Busy; addMessage = null; addedId = null
        viewModelScope.launch {
            try {
                val p = withContext(Dispatchers.IO) { Sources.resolve(text) }
                Catalog.register(p)
                addedId = p.id
                addLoad = Load.Done
                ensureShow(p.id, force = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                addLoad = Load.Failed(e.message ?: "Couldn't add that show")
            }
        }
    }

    /** Subscribe to every feed in an OPML file. Feeds load in the background, a few at a time. */
    fun importOpml(xml: String) {
        addLoad = Load.Busy; addMessage = null
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Sources.parseOpml(xml) }
                if (list.isEmpty()) {
                    addLoad = Load.Failed("No podcast feeds found in that file")
                    return@launch
                }
                list.forEach { p ->
                    Catalog.register(p)
                    stubs[p.id] = p
                    if (!subs.contains(p.id)) subs.add(p.id)
                }
                save()
                addMessage = "Imported ${list.size} show${if (list.size == 1) "" else "s"}"
                addLoad = Load.Done
                list.forEach { ensureShow(it.id) }
            } catch (e: Exception) {
                addLoad = Load.Failed("Couldn't read that file")
            }
        }
    }

    fun clearAdd() { addLoad = Load.Idle; addMessage = null; addedId = null }

    fun isSubscribed(pid: String): Boolean = subs.contains(pid)

    fun toggleSub(pid: String): Boolean {
        val on = if (subs.contains(pid)) { subs.remove(pid); false } else { remember(pid); subs.add(0, pid); true }
        save()
        if (on) ensureShow(pid)
        return on
    }

    // ---------- transcripts ----------
    private fun backendReady() = connected

    fun loadTranscript(epId: String) {
        val cur = transcripts[epId]
        if (cur is TxState.Loading || cur is TxState.Ready || cur is TxState.None || cur is TxState.Offer || cur is TxState.Generating) return
        val ep = Catalog.episode(epId) ?: return
        val url = ep.transcriptUrl
        if (url == null) {
            checkGenerated(ep)
            return
        }
        transcripts[epId] = TxState.Loading
        viewModelScope.launch {
            try {
                val lines = withContext(Dispatchers.IO) { TranscriptNet.load(url, ep.transcriptType) }
                if (lines.isEmpty()) checkGenerated(ep)
                else transcripts[epId] = TxState.Ready(lines, lines.any { it.t >= 0 })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // the publisher's file is unreachable or in a format we can't read: fall back to the server
                if (backendReady()) checkGenerated(ep) else transcripts[epId] = TxState.Failed(e.message ?: "Couldn't load the transcript")
            }
        }
    }

    /** No usable publisher transcript. Ask the server whether it already has one. */
    private fun checkGenerated(ep: Episode) {
        if (!backendReady()) {
            transcripts[ep.id] = TxState.None
            return
        }
        transcripts[ep.id] = TxState.Loading
        viewModelScope.launch {
            try {
                val st = withContext(Dispatchers.IO) { Backend.transcript(serverUrl, serverToken, ep.id) }
                applyServerTx(ep, st)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                transcripts[ep.id] = TxState.Offer("Couldn't reach your server: ${e.message ?: "unknown error"}. You can still try again.")
            }
        }
    }

    private fun applyServerTx(ep: Episode, st: Backend.Tx) {
        when (st.status) {
            "ready" -> transcripts[ep.id] =
                if (st.lines.isEmpty()) TxState.Failed("The generated transcript is empty") else TxState.Ready(st.lines, true, generated = true)
            "queued", "processing" -> { transcripts[ep.id] = TxState.Generating(st.progress); pollTranscript(ep) }
            "failed" -> transcripts[ep.id] = TxState.Failed(st.error ?: "Transcription failed")
            else -> transcripts[ep.id] = TxState.Offer()
        }
    }

    /** Ask the server to transcribe this episode's audio. It keeps working if you leave the page. */
    fun generateTranscript(epId: String) {
        val ep = Catalog.episode(epId) ?: return
        if (!backendReady()) return
        transcripts[epId] = TxState.Generating(0f)
        viewModelScope.launch {
            try {
                val st = withContext(Dispatchers.IO) { Backend.requestTranscript(serverUrl, serverToken, ep.id, ep.audioUrl, ep.durationSec) }
                applyServerTx(ep, st)
                loadUsage()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                transcripts[epId] = TxState.Failed(e.message ?: "Couldn't start transcription")
                loadUsage()
            }
        }
    }

    private fun pollTranscript(ep: Episode) {
        viewModelScope.launch {
            var tries = 0
            while (tries++ < 900) { // about an hour
                delay(4000)
                try {
                    val st = withContext(Dispatchers.IO) { Backend.transcript(serverUrl, serverToken, ep.id) }
                    if (st.status == "queued" || st.status == "processing") {
                        transcripts[ep.id] = TxState.Generating(st.progress)
                    } else {
                        applyServerTx(ep, st)
                        return@launch
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // network blip: keep trying
                }
            }
            transcripts[ep.id] = TxState.Failed("Transcription is taking too long")
        }
    }

    fun retryTranscript(epId: String) {
        transcripts.remove(epId)
        loadTranscript(epId)
    }

    // ---------- logging and ratings ----------
    fun rate(id: String, n: Int) {
        rememberEp(id)
        val cur = log[id]
        val newR = if (cur?.r == n) null else n
        val e = LogEntry(cur?.d, newR, cur?.h ?: LocalTime.now().hour)
        if (e.d == null && e.r == null) log.remove(id) else log[id] = e
        markEp(id)
        save()
        checkBadges()
    }

    fun logListen(id: String, date: String) {
        rememberEp(id)
        val cur = log[id]
        log[id] = LogEntry(date, cur?.r, cur?.h ?: LocalTime.now().hour)
        if (deleteAfter && id != playingId) Downloads.remove(app, id)
        markEp(id)
        save()
        checkBadges()
    }

    fun unlog(id: String) {
        val cur = log[id] ?: return
        if (cur.r == null) log.remove(id) else log[id] = LogEntry(null, cur.r, cur.h)
        markEp(id)
        save()
    }

    fun isLogged(id: String): Boolean = log[id]?.d != null

    // ---------- lists ----------
    fun inList(listId: String, item: ListItem): Boolean = lists.firstOrNull { it.id == listId }?.items?.contains(item) == true

    fun inWishlist(item: ListItem): Boolean = inList("wishlist", item)

    private fun rememberItem(item: ListItem) {
        if (item is ListItem.Ep) rememberEp(item.id) else remember(item.id)
    }

    fun toggleInList(listId: String, item: ListItem): Boolean {
        val l = lists.firstOrNull { it.id == listId } ?: return false
        val on = if (l.items.contains(item)) { l.items.remove(item); false } else { rememberItem(item); l.items.add(0, item); true }
        save()
        return on
    }

    fun createList(name: String, first: ListItem? = null): String {
        val id = "l" + System.currentTimeMillis()
        first?.let { rememberItem(it) }
        lists.add(UserList(id, name.trim(), if (first != null) listOf(first) else emptyList()))
        save()
        return id
    }

    fun deleteList(id: String) {
        if (id == "wishlist") return
        lists.removeAll { it.id == id }
        save()
    }

    // ---------- server: account, profile, sync ----------
    fun connectServer(url: String, inviteKey: String, handle: String, name: String) {
        val base = url.trim().trimEnd('/')
        val h = handle.trim().lowercase().removePrefix("@")
        val key = inviteKey.trim()
        if (!base.startsWith("https://")) { serverBusy = Load.Failed("The server address must start with https://"); return }
        if (!Regex("[a-z0-9_]{3,30}").matches(h)) { serverBusy = Load.Failed("Your username needs 3 to 30 letters, numbers or underscores"); return }
        if (key.isBlank()) { serverBusy = Load.Failed("Enter the invite key"); return }
        val shown = name.trim().ifBlank { profileName }.ifBlank { h }
        serverBusy = Load.Busy
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) { Server.join(base, key, h, shown, profileBio) }
                serverUrl = base; serverToken = token; serverKey = key; serverHandle = h; profileName = shown
                prefs.edit().putString("serverUrl", base).putString("serverToken", token).putString("serverKey", key)
                    .putString("serverHandle", h).putString("profileName", shown).apply()
                log.keys.forEach { dirtyEps[it] = 1 }
                showRatings.keys.forEach { dirtyShows[it] = 1 }
                persistDirty()
                serverBusy = Load.Done
                avatarFile?.let { f ->
                    try { withContext(Dispatchers.IO) { Server.putAvatar(base, token, f.readBytes()) } } catch (e: Exception) { /* uploaded next time the photo changes */ }
                }
                transcripts.clear()
                scheduleSync(300)
                loadFeed(force = true)
                searchPeople("")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                serverBusy = Load.Failed(e.message ?: "Couldn't connect")
            }
        }
    }

    /** Delete the account on the server, then forget the server on this device. */
    fun leaveServer() {
        val base = serverUrl
        val tok = serverToken
        serverBusy = Load.Busy
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Server.deleteMe(base, tok) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                serverBusy = Load.Failed("Couldn't reach the server to delete your account here (${e.message}). Try again when you are online.")
                return@launch
            }
            serverUrl = ""; serverToken = ""; serverKey = ""; serverHandle = ""
            dirtyEps.clear(); dirtyShows.clear()
            feedItems = emptyList(); people = emptyList(); profiles.clear(); group.clear(); transcripts.clear()
            feedLoad = Load.Idle; peopleLoad = Load.Idle
            prefs.edit().remove("serverUrl").remove("serverToken").remove("serverKey").remove("serverHandle").remove("dirtyEps").remove("dirtyShows").apply()
            serverBusy = Load.Idle
        }
    }

    /** Text a friend can paste into the app to join this server. */
    fun shareCode(): String? = if (connected && serverKey.isNotBlank()) ShareCode.encode(serverUrl, serverKey) else null

    fun clearServerMessage() { if (serverBusy is Load.Failed) serverBusy = Load.Idle }

    fun saveProfile(name: String, bio: String) {
        profileName = name.trim()
        profileBio = bio.trim()
        prefs.edit().putString("profileName", profileName).putString("profileBio", profileBio).apply()
        if (!connected) return
        val base = serverUrl
        val tok = serverToken
        val shown = profileName.ifBlank { serverHandle }
        val about = profileBio
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Server.patchMe(base, tok, shown, about) }
                profileError = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                profileError = "Saved on this device, but couldn't update the server: ${e.message}"
            }
        }
    }

    /** Pick a photo, or pass null to remove it. */
    fun setAvatar(uri: Uri?) {
        viewModelScope.launch {
            try {
                val bytes = if (uri == null) null else withContext(Dispatchers.IO) { AvatarImage.encode(app, uri) }
                val old = avatarFile
                val ver = if (bytes == null) 0L else System.currentTimeMillis()
                if (bytes != null) withContext(Dispatchers.IO) { File(app.filesDir, "avatar_$ver.jpg").writeBytes(bytes) }
                avatarVer = ver
                prefs.edit().putLong("avatarVer", ver).apply()
                old?.delete()
                profileError = null
                if (connected) {
                    val base = serverUrl
                    val tok = serverToken
                    try {
                        withContext(Dispatchers.IO) { if (bytes != null) Server.putAvatar(base, tok, bytes) else Server.deleteAvatar(base, tok) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        profileError = "Photo saved on this device, but couldn't upload it: ${e.message}"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                profileError = "Couldn't use that photo: ${e.message}"
            }
        }
    }

    private fun persistDirty() {
        prefs.edit().putStringSet("dirtyEps", dirtyEps.keys.toSet()).putStringSet("dirtyShows", dirtyShows.keys.toSet()).apply()
    }

    private fun markEp(id: String) {
        if (!connected) return
        dirtyEps[id] = (dirtyEps[id] ?: 0) + 1
        scheduleSync()
    }

    private fun markShow(id: String) {
        if (!connected) return
        dirtyShows[id] = (dirtyShows[id] ?: 0) + 1
        scheduleSync()
    }

    /** Send pending changes shortly after the last one, so a burst of edits becomes one request. */
    private fun scheduleSync(delayMs: Long = 4000L) {
        if (!connected) return
        persistDirty()
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(delayMs)
            runSync()
        }
    }

    private fun syncEntry(id: String): JSONObject? {
        val l = log[id]
        val j = JSONObject().put("epId", id)
        if (l == null) return j.put("showId", id.substringBefore('_')) // removed: the server drops entries with no date and no rating
        val ep = Catalog.episode(id) ?: return null
        val show = Catalog.show(ep.pid)
        j.put("showId", ep.pid).put("showName", show?.name ?: "").put("showArt", show?.artworkUrl ?: "")
            .put("epTitle", ep.title).put("genre", show?.genre ?: "").put("minutes", ep.min)
            .put("kind", if (ep.kind == Kind.SIDE) "side" else "main").put("hour", l.h).put("feedUrl", show?.feedUrl ?: "")
        l.d?.let { j.put("listenedAt", it) }
        l.r?.let { j.put("rating", it) }
        return j
    }

    private suspend fun runSync() {
        var rounds = 0
        while (connected && (dirtyEps.isNotEmpty() || dirtyShows.isNotEmpty()) && rounds++ < 20) {
            val eps = dirtyEps.entries.take(300).map { it.key to it.value }
            val shows = dirtyShows.entries.take(300).map { it.key to it.value }
            val epJson = eps.mapNotNull { syncEntry(it.first) }
            val showJson = shows.map { (id, _) ->
                JSONObject().put("showId", id).put("showName", Catalog.show(id)?.name ?: "").put("showArt", Catalog.show(id)?.artworkUrl ?: "")
                    .put("rating", showRatings[id] ?: JSONObject.NULL)
            }
            val badges = JSONArray()
            computeBadges(log.toMap()).filter { it.earned }.forEach { badges.put(JSONObject().put("id", it.def.id).put("tier", it.tier)) }
            val base = serverUrl
            val tok = serverToken
            try {
                withContext(Dispatchers.IO) { Server.push(base, tok, epJson, showJson, badges) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                syncJob = viewModelScope.launch { delay(60_000L); runSync() } // offline or server down: try again in a minute
                return
            }
            eps.forEach { (id, v) -> if (dirtyEps[id] == v) dirtyEps.remove(id) }
            shows.forEach { (id, v) -> if (dirtyShows[id] == v) dirtyShows.remove(id) }
            persistDirty()
        }
    }

    // ---------- friends ----------
    fun loadFeed(force: Boolean = false) {
        if (!connected || feedLoad is Load.Busy) return
        if (!force && feedLoad is Load.Done) return
        feedLoad = Load.Busy
        val base = serverUrl
        val tok = serverToken
        viewModelScope.launch {
            try {
                feedItems = withContext(Dispatchers.IO) { Server.feed(base, tok) }
                feedLoad = Load.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                feedLoad = Load.Failed(e.message ?: "Couldn't load the feed")
            }
        }
    }

    /** Everyone on the server, people you follow first. An empty query lists the newest members. */
    fun searchPeople(q: String) {
        if (!connected) return
        peopleLoad = Load.Busy
        val base = serverUrl
        val tok = serverToken
        viewModelScope.launch {
            try {
                people = withContext(Dispatchers.IO) { Server.people(base, tok, q.trim()) }
                peopleLoad = Load.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                peopleLoad = Load.Failed(e.message ?: "Couldn't load people")
            }
        }
    }

    fun toggleFollow(handle: String) {
        if (!connected) return
        val was = people.firstOrNull { it.handle == handle }?.following ?: profiles[handle]?.isFollowing ?: false
        val on = !was
        fun setFollowing(v: Boolean) {
            people = people.map { if (it.handle == handle) it.copy(following = v) else it }
            profiles[handle]?.let { profiles[handle] = it.copy(isFollowing = v) }
        }
        setFollowing(on)
        val base = serverUrl
        val tok = serverToken
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Server.follow(base, tok, handle, on) }
                loadFeed(force = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                setFollowing(was)
            }
        }
    }

    fun loadProfile(handle: String, force: Boolean = false) {
        if (!connected || profileLoad[handle] is Load.Busy) return
        if (!force && profiles.containsKey(handle) && profileLoad[handle] is Load.Done) return
        profileLoad[handle] = Load.Busy
        val base = serverUrl
        val tok = serverToken
        viewModelScope.launch {
            try {
                profiles[handle] = withContext(Dispatchers.IO) { Server.profile(base, tok, handle) }
                profileLoad[handle] = Load.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                profileLoad[handle] = Load.Failed(e.message ?: "Couldn't load this profile")
            }
        }
    }

    /** Make a show someone else listened to known here, so its page can be opened. Returns false if its feed address is missing. */
    fun adoptShow(showId: String, name: String, art: String, feedUrl: String): Boolean {
        if (Catalog.show(showId) != null) return true
        if (!feedUrl.startsWith("http")) return false
        Catalog.register(Podcast(showId, name.ifBlank { "Podcast" }, "", "", "", feedUrl, art))
        return true
    }

    // ---------- show ratings ----------
    fun rateShow(pid: String, n: Int) {
        remember(pid)
        if (showRatings[pid] == n) showRatings.remove(pid) else showRatings[pid] = n
        markShow(pid)
        save()
    }

    /** Average of your own episode ratings for a show, and how many episodes that covers. */
    fun showAverage(pid: String): Pair<Double, Int>? {
        var sum = 0
        var n = 0
        val prefix = pid + "_"
        for ((id, l) in log) {
            val r = l.r ?: continue
            if (id.startsWith(prefix)) { sum += r; n++ }
        }
        return if (n == 0) null else (sum.toDouble() / n) to n
    }

    fun loadGroupRatings(pid: String) {
        if (!connected) return
        val base = serverUrl
        val tok = serverToken
        viewModelScope.launch {
            try {
                group[pid] = withContext(Dispatchers.IO) { Server.groupRatings(base, tok, pid) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // group numbers are a bonus: stay quiet when the server is unreachable
            }
        }
    }

    // ---------- player ----------
    private fun connect() {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        controllerFuture = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                c.setPlaybackSpeed(speed)
                val mid = c.currentMediaItem?.mediaId
                if (mid != null && Catalog.episode(mid) != null) {
                    playingId = mid
                    durationSec = Catalog.episode(mid)?.durationSec ?: 0
                }
                syncState()
                val ops = ArrayList(pending)
                pending.clear()
                ops.forEach { it(c) }
            } catch (e: Exception) {
                playerError = "Couldn't start the audio player"
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(op: (MediaController) -> Unit) {
        val c = controller
        if (c != null) op(c) else pending.add(op)
    }

    private fun syncState() {
        val c = controller ?: return
        val st = c.playbackState
        val was = playing
        playing = c.playWhenReady && (st == Player.STATE_READY || st == Player.STATE_BUFFERING)
        buffering = c.playWhenReady && st == Player.STATE_BUFFERING
        if (was != playing) publishWidgets()
    }

    private fun tick() {
        if (sleepEndsAt > 0L && System.currentTimeMillis() >= sleepEndsAt) {
            sleepEndsAt = 0L
            controller?.pause()
            saveProgress()
        }
        val c = controller ?: return
        val id = playingId ?: return
        if (c.currentMediaItem?.mediaId != id) return
        val st = c.playbackState
        if (st == Player.STATE_IDLE || st == Player.STATE_ENDED) return
        t = c.currentPosition / 1000f
        val d = c.duration
        if (d != C.TIME_UNSET && d > 0) durationSec = (d / 1000).toInt()
        if (playing && ++ticks % 20 == 0) saveProgress(periodic = true)
    }

    private fun saveProgress(periodic: Boolean = false) {
        val id = playingId ?: return
        val d = if (durationSec > 0) durationSec else Catalog.episode(id)?.durationSec ?: 0
        if (t > 5 && (d == 0 || t < d - 10)) prog[id] = t.toInt() else prog.remove(id)
        save(widgets = !periodic)
    }

    private fun buildItem(ep: Episode): MediaItem {
        val show = Catalog.show(ep.pid)
        val art = ep.imageUrl.ifBlank { show?.artworkUrl ?: "" }
        val meta = MediaMetadata.Builder()
            .setTitle(ep.title)
            .setArtist(show?.name ?: "")
            .setAlbumTitle(show?.name ?: "")
            .setIsPlayable(true)
            .apply { if (art.isNotBlank()) setArtworkUri(Uri.parse(art)) }
            .build()
        return MediaItem.Builder().setMediaId(ep.id).setUri(Downloads.localUri(ep.id) ?: Uri.parse(ep.audioUrl)).setMediaMetadata(meta).build()
    }

    fun play(id: String, at: Int? = null) {
        val ep = Catalog.episode(id)
        if (ep == null) {
            // the show isn't loaded yet (for example right after a restart): load it, then start
            pendingPlay = id
            nowMeta?.takeIf { it.id == id }?.let { ensureShow(it.pid, force = true) }
            return
        }
        if (ep.audioUrl.isBlank()) return
        playerError = null
        if (playingId != null && playingId != id) saveProgress()
        val saved = prog[id] ?: 0
        val start = at ?: if (ep.durationSec > 0 && saved >= ep.durationSec - 10) 0 else saved
        playingId = id
        t = start.toFloat()
        durationSec = ep.durationSec
        remember(ep.pid)
        val show = Catalog.show(ep.pid)
        nowMeta = NowMeta(id, ep.pid, ep.title, show?.name ?: "", art(ep), ep.durationSec)
        save()
        autoDownloadAhead(ep)
        withController { c ->
            val st = c.playbackState
            if (c.currentMediaItem?.mediaId == id && st != Player.STATE_IDLE && st != Player.STATE_ENDED) {
                if (at != null) c.seekTo(at * 1000L)
                c.play()
            } else {
                c.setMediaItem(buildItem(ep), start * 1000L)
                c.prepare()
                c.play()
            }
        }
    }

    fun togglePlay() {
        val id = playingId ?: return
        playerError = null
        withController { c ->
            val st = c.playbackState
            when {
                c.currentMediaItem?.mediaId != id || st == Player.STATE_IDLE -> play(id)
                st == Player.STATE_ENDED -> play(id, 0)
                c.playWhenReady -> { c.pause(); saveProgress() }
                else -> c.play()
            }
        }
    }

    fun seekTo(sec: Float) {
        val id = playingId ?: return
        val max = if (durationSec > 0) durationSec.toFloat() else Float.MAX_VALUE
        val s = sec.coerceIn(0f, max)
        t = s
        val c = controller
        if (c != null && c.currentMediaItem?.mediaId == id && c.playbackState != Player.STATE_IDLE) {
            c.seekTo((s * 1000).toLong())
        } else {
            prog[id] = s.toInt()
        }
    }

    fun skip(delta: Int) = seekTo(t + delta)

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
        controller?.setPlaybackSpeed(speed)
        save()
    }

    private fun finish() {
        val id = playingId ?: return
        playing = false
        buffering = false
        if (durationSec > 0) t = durationSec.toFloat()
        prog.remove(id)
        val cur = log[id]
        if (cur?.d == null) log[id] = LogEntry(Dates.today(), cur?.r, LocalTime.now().hour)
        markEp(id)
        finishedId = id
        if (deleteAfter) Downloads.remove(app, id)
        Catalog.episode(id)?.let { autoDownloadAhead(it) }
        save()
        checkBadges()
    }

    fun dismissFinished() { finishedId = null }

    override fun onCleared() {
        saveProgress()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
