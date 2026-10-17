package app.leestn.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.updateAll
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.json.JSONArray
import org.json.JSONObject

/** What the home-screen widgets show. The app and the playback service write it, the widgets only read it. */
data class WNow(val id: String, val title: String, val show: String, val art: String, val playing: Boolean, val pos: Int, val dur: Int) {
    val frac: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

data class WItem(val id: String, val title: String, val show: String, val art: String, val sub: String, val frac: Float)

/** [week] is minutes listened on each of the last 7 days, oldest first. */
data class WStats(val year: String, val hours: String, val episodes: Int, val streak: Int, val avg: String, val week: List<Int> = List(7) { 0 })

data class WSnapshot(val now: WNow?, val cont: List<WItem>, val fresh: List<WItem>, val stats: WStats?)

object WidgetStore {
    private const val PREFS = "leestn_widget"

    fun read(ctx: Context): WSnapshot {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("snap", null)
            ?: return WSnapshot(null, emptyList(), emptyList(), null)
        return try {
            val j = JSONObject(raw)
            WSnapshot(
                now = j.optJSONObject("now")?.let {
                    WNow(it.getString("id"), it.getString("title"), it.optString("show"), it.optString("art"), it.optBoolean("playing"), it.optInt("pos"), it.optInt("dur"))
                },
                cont = items(j.optJSONArray("cont")),
                fresh = items(j.optJSONArray("fresh")),
                stats = j.optJSONObject("stats")?.let {
                    WStats(
                        it.optString("year"), it.optString("hours"), it.optInt("episodes"), it.optInt("streak"), it.optString("avg"),
                        it.optJSONArray("week")?.let { a -> List(a.length()) { i -> a.optInt(i) } } ?: List(7) { 0 },
                    )
                },
            )
        } catch (e: Exception) {
            WSnapshot(null, emptyList(), emptyList(), null)
        }
    }

    private fun items(a: JSONArray?): List<WItem> {
        if (a == null) return emptyList()
        val out = ArrayList<WItem>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(WItem(o.getString("id"), o.getString("title"), o.optString("show"), o.optString("art"), o.optString("sub"), o.optDouble("frac", 0.0).toFloat()))
        }
        return out
    }

    private fun write(ctx: Context, s: WSnapshot) {
        val j = JSONObject()
        s.now?.let {
            j.put("now", JSONObject().put("id", it.id).put("title", it.title).put("show", it.show).put("art", it.art).put("playing", it.playing).put("pos", it.pos).put("dur", it.dur))
        }
        fun arr(l: List<WItem>) = JSONArray().also { a ->
            l.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("show", it.show).put("art", it.art).put("sub", it.sub).put("frac", it.frac.toDouble())) }
        }
        j.put("cont", arr(s.cont)).put("fresh", arr(s.fresh))
        s.stats?.let { j.put("stats", JSONObject().put("year", it.year).put("hours", it.hours).put("episodes", it.episodes).put("streak", it.streak).put("avg", it.avg).put("week", JSONArray(it.week))) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("snap", j.toString()).apply()
    }

    suspend fun publish(ctx: Context, s: WSnapshot) {
        val app = ctx.applicationContext
        if (s == read(app)) return
        write(app, s)
        refresh(app)
    }

    /** Light update from the playback service: only the play state and position of the current episode. */
    suspend fun patchNow(ctx: Context, playing: Boolean, pos: Int) {
        val app = ctx.applicationContext
        val cur = read(app)
        val now = cur.now ?: return
        val next = now.copy(playing = playing, pos = pos)
        if (next == now) return
        write(app, cur.copy(now = next))
        refresh(app)
    }

    suspend fun refresh(ctx: Context) {
        NowPlayingWidget().updateAll(ctx)
        MiniPlayerWidget().updateAll(ctx)
        ContinueWidget().updateAll(ctx)
        NewEpisodesWidget().updateAll(ctx)
        StatsWidget().updateAll(ctx)
    }
}

object WidgetArt {
    suspend fun bitmap(ctx: Context, url: String, px: Int = 256): Bitmap? {
        if (url.isBlank()) return null
        return try {
            val req = ImageRequest.Builder(ctx).data(url).size(px).allowHardware(false).build()
            (ImageLoader(ctx).execute(req) as? SuccessResult)?.drawable?.toBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
