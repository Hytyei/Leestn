package app.leestn.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.updateAll
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.json.JSONArray
import org.json.JSONObject

/** What the home-screen widgets show. The app and the playback service write it, the widgets only read it. */
data class WNow(val id: String, val title: String, val show: String, val art: String, val playing: Boolean, val pos: Int, val dur: Int) {
    val frac: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

data class WItem(val id: String, val title: String, val show: String, val art: String, val sub: String, val frac: Float, val leftMin: Int = 0)

/** The show listened to last and what you did with it. [when] is a short phrase such as "2 days ago"; [rating] is 0 when unrated. */
data class WLast(
    val epId: String, val epTitle: String, val show: String, val art: String, val `when`: String,
    val showEps: Int, val showMin: Int, val rating: Int, val showAvg: String, val streak: Int,
)

/** [week] is minutes listened on each of the last 7 days, oldest first. */
data class WStats(val year: String, val hours: String, val episodes: Int, val streak: Int, val avg: String, val week: List<Int> = List(7) { 0 })

data class WSnapshot(val now: WNow?, val cont: List<WItem>, val fresh: List<WItem>, val stats: WStats?, val last: WLast? = null)

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
                last = j.optJSONObject("last")?.let {
                    WLast(
                        it.optString("epId"), it.optString("epTitle"), it.optString("show"), it.optString("art"), it.optString("when"),
                        it.optInt("showEps"), it.optInt("showMin"), it.optInt("rating"), it.optString("showAvg"), it.optInt("streak"),
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
            out.add(WItem(o.getString("id"), o.getString("title"), o.optString("show"), o.optString("art"), o.optString("sub"), o.optDouble("frac", 0.0).toFloat(), o.optInt("left", 0)))
        }
        return out
    }

    private fun write(ctx: Context, s: WSnapshot) {
        val j = JSONObject()
        s.now?.let {
            j.put("now", JSONObject().put("id", it.id).put("title", it.title).put("show", it.show).put("art", it.art).put("playing", it.playing).put("pos", it.pos).put("dur", it.dur))
        }
        fun arr(l: List<WItem>) = JSONArray().also { a ->
            l.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("show", it.show).put("art", it.art).put("sub", it.sub).put("frac", it.frac.toDouble()).put("left", it.leftMin)) }
        }
        j.put("cont", arr(s.cont)).put("fresh", arr(s.fresh))
        s.stats?.let { j.put("stats", JSONObject().put("year", it.year).put("hours", it.hours).put("episodes", it.episodes).put("streak", it.streak).put("avg", it.avg).put("week", JSONArray(it.week))) }
        s.last?.let {
            j.put("last", JSONObject().put("epId", it.epId).put("epTitle", it.epTitle).put("show", it.show).put("art", it.art).put("when", it.`when`)
                .put("showEps", it.showEps).put("showMin", it.showMin).put("rating", it.rating).put("showAvg", it.showAvg).put("streak", it.streak))
        }
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
        LastActivityWidget().updateAll(ctx)
    }
}

/**
 * Pictures for the widgets. Widgets cannot draw gradients, rounded images or arcs themselves on every Android version,
 * so each of those is painted into a small bitmap here. Every size is given in dp and scaled down if the bitmap
 * would get too big for a RemoteViews (the widget system rejects large bitmaps).
 */
object WidgetArt {
    private const val MAX_AREA = 240_000.0
    val RED: Int = 0xFF7B5CF5.toInt() // the app's violet accent
    val BG: Int = 0xFF121117.toInt()

    suspend fun bitmap(ctx: Context, url: String, px: Int = 256): Bitmap? {
        if (url.isBlank()) return null
        return try {
            val req = ImageRequest.Builder(ctx).data(url).size(px).allowHardware(false).build()
            (Coil.imageLoader(ctx).execute(req) as? SuccessResult)?.drawable?.toBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /** Pixels for a size in dp: width, height and pixels per dp after any scaling down. */
    private class Dim(val w: Int, val h: Int, val k: Float)

    private fun dim(wDp: Float, hDp: Float, density: Float): Dim {
        var k = density
        val area = (wDp * k).toDouble() * (hDp * k).toDouble()
        if (area > MAX_AREA) k = (k * Math.sqrt(MAX_AREA / area)).toFloat()
        return Dim(maxOf(2, (wDp * k).toInt()), maxOf(2, (hDp * k).toInt()), k)
    }

    private fun blank(d: Dim): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(d.w, d.h, Bitmap.Config.ARGB_8888)
        return b to Canvas(b)
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    fun mix(a: Int, b: Int, t: Float): Int = ColorUtils.blendARGB(a, b, t)

    /** The cover's dominant vivid colour, nudged so it always reads well on the dark widget. Red when the cover is grey. */
    fun tint(bmp: Bitmap?): Int {
        if (bmp == null || bmp.width < 1 || bmp.height < 1) return RED
        return try {
            val s = Bitmap.createScaledBitmap(bmp, 24, 24, true)
            var r = 0.0; var g = 0.0; var b = 0.0; var sum = 0.0
            val hsv = FloatArray(3)
            for (y in 0 until 24) for (x in 0 until 24) {
                val c = s.getPixel(x, y)
                Color.colorToHSV(c, hsv)
                val w = (hsv[1] * hsv[2]).toDouble() + 0.02
                r += Color.red(c) * w; g += Color.green(c) * w; b += Color.blue(c) * w; sum += w
            }
            val avg = Color.rgb((r / sum).toInt(), (g / sum).toInt(), (b / sum).toInt())
            Color.colorToHSV(avg, hsv)
            if (hsv[1] < 0.18f) return RED
            hsv[1] = hsv[1].coerceIn(0.5f, 0.9f)
            hsv[2] = hsv[2].coerceIn(0.6f, 0.95f)
            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            RED
        }
    }

    /** Card background: the cover's colour fading into near-black, with rounded corners and a faint edge. */
    fun backdrop(wDp: Float, hDp: Float, cornerDp: Float, tint: Int, density: Float, strength: Float = 0.42f): Bitmap {
        val d = dim(wDp, hDp, density)
        val (bmp, c) = blank(d)
        val corner = cornerDp * d.k
        val rect = RectF(0f, 0f, d.w.toFloat(), d.h.toFloat())
        val p = paint()
        p.shader = LinearGradient(
            0f, 0f, d.w * 0.85f, d.h.toFloat(),
            intArrayOf(mix(BG, tint, strength), mix(BG, tint, strength * 0.35f), BG),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(rect, corner, corner, p)
        val edge = paint()
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = maxOf(1f, d.k * 0.75f)
        edge.color = 0x1AFFFFFF
        val half = edge.strokeWidth / 2f
        c.drawRoundRect(RectF(half, half, d.w - half, d.h - half), corner - half, corner - half, edge)
        return bmp
    }

    /** Cover fitted to a rounded box. [src] null draws a gradient placeholder. */
    fun roundedCrop(src: Bitmap?, wDp: Float, hDp: Float, cornerDp: Float, density: Float, tint: Int = RED): Bitmap {
        val d = dim(wDp, hDp, density)
        val (bmp, c) = blank(d)
        val corner = cornerDp * d.k
        val rect = RectF(0f, 0f, d.w.toFloat(), d.h.toFloat())
        val p = paint()
        if (src == null || src.width < 1 || src.height < 1) {
            p.shader = LinearGradient(0f, 0f, d.w.toFloat(), d.h.toFloat(), mix(0xFF3A2A7A.toInt(), tint, 0.3f), 0xFF1A1626.toInt(), Shader.TileMode.CLAMP)
        } else {
            val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(d.w.toFloat() / src.width, d.h.toFloat() / src.height)
            val m = Matrix()
            m.setScale(scale, scale)
            m.postTranslate((d.w - src.width * scale) / 2f, (d.h - src.height * scale) / 2f)
            shader.setLocalMatrix(m)
            p.shader = shader
        }
        c.drawRoundRect(rect, corner, corner, p)
        return bmp
    }

    /** Fade from clear to the cover's colour at the bottom, for text over a full-bleed cover. */
    fun scrim(wDp: Float, hDp: Float, cornerDp: Float, tint: Int, density: Float): Bitmap {
        val d = dim(wDp, hDp, density)
        val (bmp, c) = blank(d)
        val corner = cornerDp * d.k
        val dark = mix(BG, tint, 0.3f)
        val p = paint()
        p.shader = LinearGradient(
            0f, 0f, 0f, d.h.toFloat(),
            intArrayOf(0x00000000, (dark and 0x00FFFFFF) or 0x40000000, (dark and 0x00FFFFFF) or 0xF2000000.toInt()),
            floatArrayOf(0.15f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(RectF(0f, 0f, d.w.toFloat(), d.h.toFloat()), corner, corner, p)
        return bmp
    }

    /** Rounded progress bar with a gradient fill and a round knob. [hDp] is the knob size; the bar is thinner. */
    fun progressPill(wDp: Float, hDp: Float, frac: Float, color: Int, density: Float): Bitmap {
        val d = dim(wDp, hDp, density)
        val (bmp, c) = blank(d)
        val f = frac.coerceIn(0f, 1f)
        val barH = d.h * 0.42f
        val top = (d.h - barH) / 2f
        val r = barH / 2f
        val track = paint()
        track.color = 0x33FFFFFF
        c.drawRoundRect(RectF(0f, top, d.w.toFloat(), top + barH), r, r, track)
        if (f > 0f) {
            val knobR = d.h / 2f - 0.5f
            val end = (knobR + (d.w - 2 * knobR) * f).coerceIn(knobR, d.w - knobR)
            val fill = paint()
            fill.shader = LinearGradient(0f, 0f, maxOf(end, 1f), 0f, mix(color, Color.WHITE, 0.35f), color, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(0f, top, end, top + barH), r, r, fill)
            val glow = paint()
            glow.color = (color and 0x00FFFFFF) or 0x55000000
            c.drawCircle(end, d.h / 2f, knobR, glow)
            val knob = paint()
            knob.color = Color.WHITE
            c.drawCircle(end, d.h / 2f, knobR * 0.62f, knob)
        }
        return bmp
    }

    /** A bar for each value, oldest first. The last bar (today) is full colour, the others softer, empty days a faint stub. */
    fun weekChart(wDp: Float, hDp: Float, values: List<Int>, color: Int, gapDp: Float, density: Float): Bitmap {
        val d = dim(wDp, hDp, density)
        val (bmp, c) = blank(d)
        val n = maxOf(values.size, 1)
        val gap = gapDp * d.k
        val barW = (d.w - gap * (n - 1)) / n
        val corner = barW * 0.34f
        val peak = maxOf(values.maxOrNull() ?: 0, 1)
        val minH = minOf(d.h.toFloat(), maxOf(4f * d.k, barW * 0.5f))
        values.forEachIndexed { i, v ->
            val x = i * (barW + gap)
            val track = paint()
            track.color = 0x16FFFFFF
            c.drawRoundRect(RectF(x, 0f, x + barW, d.h.toFloat()), corner, corner, track)
            if (v > 0) {
                val h = maxOf(minH, d.h * v.toFloat() / peak)
                val today = i == values.size - 1
                val fill = paint()
                val bottom = mix(color, BG, if (today) 0f else 0.35f)
                val topC = mix(color, Color.WHITE, if (today) 0.4f else 0.12f)
                fill.shader = LinearGradient(0f, d.h - h, 0f, d.h.toFloat(), topC, bottom, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(x, d.h - h, x + barW, d.h.toFloat()), corner, corner, fill)
            }
        }
        return bmp
    }

    /** A ring split into one segment per entry, lit where the entry is true. The last segment is the brightest. */
    fun ring(sizeDp: Float, lit: List<Boolean>, color: Int, density: Float): Bitmap {
        val d = dim(sizeDp, sizeDp, density)
        val (bmp, c) = blank(d)
        val n = maxOf(lit.size, 1)
        val stroke = d.w * 0.13f
        val box = RectF(stroke / 2f, stroke / 2f, d.w - stroke / 2f, d.h - stroke / 2f)
        val gapDeg = 12f
        val sweep = 360f / n - gapDeg
        lit.forEachIndexed { i, on ->
            val p = paint()
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke
            p.color = if (on) (if (i == lit.size - 1) mix(color, Color.WHITE, 0.3f) else color) else 0x22FFFFFF
            c.drawArc(box, -90f + i * (360f / n) + gapDeg / 2f, sweep, false, p)
        }
        return bmp
    }
}
