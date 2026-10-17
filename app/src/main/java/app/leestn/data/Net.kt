package app.leestn.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Blocking HTTP helpers. Always call from a background dispatcher. */
object Http {
    private const val UA = "Leestn/1.0 (Android podcast app)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun request(url: String): Request = Request.Builder().url(url).header("User-Agent", UA).build()

    fun text(url: String, maxBytes: Long = 8_000_000): String {
        client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response")
            val len = body.contentLength()
            if (len > maxBytes) throw IOException("Response too large")
            return body.string()
        }
    }

    fun download(url: String, target: File, maxBytes: Long = 60_000_000) {
        client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response")
            var total = 0L
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("Feed too large")
                        out.write(buf, 0, n)
                    }
                }
            }
        }
    }
}

/** Apple's public podcast directory. No API key needed. */
object Itunes {
    fun idFor(feedUrl: String): String = Text.sha1(feedUrl.trim()).substring(0, 10)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun bigArt(url: String): String =
        url.replace("100x100", "600x600").replace("200x200", "600x600").replace("170x170", "600x600")

    private fun fromResult(o: JSONObject): Podcast? {
        val feed = o.optString("feedUrl", "").trim()
        val name = o.optString("collectionName", o.optString("trackName", "")).trim()
        if (feed.isEmpty() || name.isEmpty()) return null
        val art = o.optString("artworkUrl600", "").ifEmpty { bigArt(o.optString("artworkUrl100", "")) }
        return Podcast(
            id = idFor(feed),
            name = name,
            host = o.optString("artistName", ""),
            genre = o.optString("primaryGenreName", ""),
            tagline = "",
            feedUrl = feed,
            artworkUrl = art,
        )
    }

    private fun parseResults(json: String): List<Podcast> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Podcast>()
        for (i in 0 until arr.length()) {
            val p = fromResult(arr.optJSONObject(i) ?: continue)
            if (p != null) out.add(p)
        }
        return out.distinctBy { it.id }
    }

    fun search(term: String, limit: Int = 30): List<Podcast> {
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=$limit&term=${enc(term.trim())}"
        return parseResults(Http.text(url))
    }

    /** Look up feed URLs for a list of iTunes collection ids, keeping the order given. */
    fun lookup(ids: List<String>): List<Podcast> {
        if (ids.isEmpty()) return emptyList()
        val json = Http.text("https://itunes.apple.com/lookup?entity=podcast&id=${ids.joinToString(",")}")
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val byId = HashMap<String, Podcast>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val p = fromResult(o) ?: continue
            byId[o.optLong("collectionId").toString()] = p
        }
        return ids.distinct().mapNotNull { byId[it] }
    }

    /** Current top podcasts in the user's country, falling back to the US chart. */
    fun top(limit: Int = 24): List<Podcast> {
        val cc = Locale.getDefault().country.lowercase().ifEmpty { "us" }
        val ids = try {
            chartIds(cc, limit)
        } catch (e: Exception) {
            if (cc == "us") throw e
            chartIds("us", limit)
        }
        return lookup(ids)
    }

    private fun chartIds(cc: String, limit: Int): List<String> {
        val json = Http.text("https://rss.applemarketingtools.com/api/v2/$cc/podcasts/top/$limit/podcasts.json")
        val arr = JSONObject(json).getJSONObject("feed").optJSONArray("results") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).optString("id", "")
            if (id.isNotEmpty()) out.add(id)
        }
        return out
    }
}

/** Downloads and parses a published transcript. Supported: JSON, WebVTT, SRT and plain text. */
object TranscriptNet {
    fun load(url: String, type: String?): List<TxLine> {
        val raw = Http.text(url, maxBytes = 6_000_000)
        val t = (type ?: "").lowercase()
        val trimmed = raw.trimStart()
        return when {
            t.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[") -> fromJson(trimmed)
            t.contains("vtt") || t.contains("srt") || t.contains("subrip") || trimmed.startsWith("WEBVTT") || raw.contains("-->") -> TranscriptParse.fromTimed(raw)
            else -> TranscriptParse.fromPlain(raw)
        }
    }

    /** Podcasting 2.0 JSON: { "segments": [ { "speaker", "startTime", "body" } ] } or the same array at top level. */
    private fun fromJson(raw: String): List<TxLine> {
        val arr: JSONArray = if (raw.startsWith("[")) JSONArray(raw) else (JSONObject(raw).optJSONArray("segments") ?: return emptyList())
        val cues = ArrayList<Cue>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val body = s.optString("body", "").trim()
            if (body.isEmpty()) continue
            cues.add(Cue(s.optDouble("startTime", 0.0), s.optString("speaker", "").trim(), body))
        }
        return TranscriptParse.group(cues)
    }
}
