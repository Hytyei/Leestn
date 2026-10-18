package app.leestn.data

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** One person on a Leestn server. */
data class Usage(val limitMin: Int, val usedMin: Double, val remainingMin: Double, val resetsAt: Long)
data class RPerson(val handle: String, val name: String, val bio: String, val avatarV: Long, val following: Boolean)

/** One thing a followed person listened to. */
data class RFeedItem(
    val id: Long, val at: Long, val handle: String, val name: String, val avatarV: Long,
    val epId: String, val epTitle: String, val showId: String, val showName: String, val showArt: String, val feedUrl: String,
    val rating: Int?, val listenedAt: String,
) {
    val key: String get() = "$handle:$epId"
}

data class RRecent(val epId: String, val epTitle: String, val showId: String, val showName: String, val showArt: String, val feedUrl: String, val rating: Int?, val listenedAt: String)
data class RShow(val showId: String, val showName: String, val showArt: String, val feedUrl: String, val episodes: Int, val minutes: Int, val avgRating: Double?)
data class RShowRating(val showId: String, val showName: String, val showArt: String, val rating: Int)
data class RBadge(val id: String, val tier: Int)

data class RProfile(
    val handle: String, val name: String, val bio: String, val avatarV: Long,
    val followers: Int, val following: Int, val isFollowing: Boolean, val isMe: Boolean,
    val episodes: Int, val hours: Double, val avgRating: Double?,
    val topShows: List<RShow>, val recent: List<RRecent>, val showRatings: List<RShowRating>, val badges: List<RBadge>,
)

/** What everyone on the server thinks of a show. */
data class GroupRatings(
    val showAvg: Double?, val showN: Int, val epsAvg: Double?, val epsN: Int,
    val episodes: Map<String, Pair<Double, Int>>,
)

/** The text a friend pastes to join your server: its address and the invite key. */
object ShareCode {
    private const val PREFIX = "LSTN-"

    fun encode(url: String, key: String): String {
        val j = JSONObject().put("v", 1).put("u", url).put("k", key).toString()
        return PREFIX + Base64.encodeToString(j.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Accepts the bare code or a whole message containing it. Returns the server address and key, or null. */
    fun decode(raw: String): Pair<String, String>? {
        val m = Regex("LSTN-([A-Za-z0-9_\\-]+)").find(raw.trim()) ?: return null
        return try {
            val bytes = Base64.decode(m.groupValues[1], Base64.URL_SAFE or Base64.NO_WRAP)
            val j = JSONObject(String(bytes, Charsets.UTF_8))
            val u = j.getString("u").trim().trimEnd('/')
            val k = j.getString("k")
            if (u.startsWith("https://") && k.isNotBlank()) u to k else null
        } catch (e: Exception) {
            null
        }
    }
}

/** Blocking calls to a Leestn Worker (see the backend folder). Always call from a background dispatcher. */
object Server {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(method: String, base: String, token: String, path: String, json: JSONObject? = null, bytes: ByteArray? = null): JSONObject {
        val body: RequestBody? = when {
            bytes != null -> bytes.toRequestBody("image/jpeg".toMediaType())
            json != null -> json.toString().toRequestBody("application/json".toMediaType())
            method == "GET" || method == "HEAD" -> null
            else -> "".toRequestBody(null)
        }
        val b = Request.Builder().url(base + path).header("User-Agent", "Leestn/1.0 (Android)").method(method, body)
        if (token.isNotBlank()) b.header("Authorization", "Bearer $token")
        Http.client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = try { JSONObject(text).optString("error") } catch (e: Exception) { "" }
                throw IOException(msg.ifBlank { "Server error ${resp.code}" })
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    fun avatarUrl(base: String, handle: String, v: Long): String = "$base/v1/users/${enc(handle)}/avatar?v=$v"

    private fun optInt(o: JSONObject, k: String): Int? = if (o.isNull(k) || !o.has(k)) null else o.optInt(k)
    private fun optDbl(o: JSONObject, k: String): Double? = if (o.isNull(k) || !o.has(k)) null else o.optDouble(k).takeIf { !it.isNaN() }

    /** Create an account. [inviteKey] is the server's invite key. Returns the new account's secret token. */
    fun join(base: String, inviteKey: String, handle: String, name: String, bio: String): String {
        val j = call("POST", base, inviteKey, "/v1/users", JSONObject().put("handle", handle).put("name", name).put("bio", bio))
        return j.getString("token")
    }

    fun patchMe(base: String, token: String, name: String, bio: String) {
        call("PATCH", base, token, "/v1/me", JSONObject().put("name", name).put("bio", bio))
    }

    fun putAvatar(base: String, token: String, jpeg: ByteArray): Long = call("PUT", base, token, "/v1/me/avatar", bytes = jpeg).optLong("avatarV")

    fun deleteAvatar(base: String, token: String) {
        call("DELETE", base, token, "/v1/me/avatar")
    }

    fun deleteMe(base: String, token: String) {
        call("DELETE", base, token, "/v1/me")
    }

    /** Own account: handle, name, bio, avatar version. */
    fun me(base: String, token: String): RPerson {
        val j = call("GET", base, token, "/v1/me")
        return RPerson(j.getString("handle"), j.optString("name"), j.optString("bio"), j.optLong("avatarV"), false)
    }

    /** Today's free Whisper allowance on the server. Read-only. */
    fun usage(base: String, token: String): Usage {
        val j = call("GET", base, token, "/v1/usage")
        return Usage(j.optInt("limitMin"), j.optDouble("usedMin"), j.optDouble("remainingMin"), j.optLong("resetsAt"))
    }

    fun push(base: String, token: String, log: List<JSONObject>, showRatings: List<JSONObject>, badges: JSONArray?) {
        val j = JSONObject()
        j.put("log", JSONArray(log))
        j.put("showRatings", JSONArray(showRatings))
        if (badges != null) j.put("badges", badges)
        call("PUT", base, token, "/v1/sync", j)
    }

    fun people(base: String, token: String, query: String): List<RPerson> {
        val j = call("GET", base, token, "/v1/users?q=" + enc(query))
        val a = j.optJSONArray("users") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            RPerson(o.getString("handle"), o.optString("name"), o.optString("bio"), o.optLong("avatarV"), o.optBoolean("following"))
        }
    }

    fun follow(base: String, token: String, handle: String, on: Boolean) {
        call(if (on) "POST" else "DELETE", base, token, "/v1/follow/" + enc(handle))
    }

    fun feed(base: String, token: String): List<RFeedItem> {
        val j = call("GET", base, token, "/v1/feed")
        val a = j.optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val u = o.optJSONObject("user") ?: return@mapNotNull null
            if (o.optString("type") != "listen") return@mapNotNull null
            RFeedItem(
                o.optLong("id"), o.optLong("at"), u.optString("handle"), u.optString("name"), u.optLong("avatarV"),
                o.optString("epId"), o.optString("epTitle"), o.optString("showId"), o.optString("showName"), o.optString("showArt"),
                o.optString("feedUrl"), optInt(o, "rating"), o.optString("listenedAt"),
            )
        }
    }

    fun profile(base: String, token: String, handle: String): RProfile {
        val j = call("GET", base, token, "/v1/users/" + enc(handle))
        val st = j.optJSONObject("stats") ?: JSONObject()
        val tops = ArrayList<RShow>()
        st.optJSONArray("topShows")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                tops.add(RShow(o.optString("show_id"), o.optString("show_name"), o.optString("show_art"), o.optString("feed_url"), o.optInt("episodes"), o.optInt("minutes"), optDbl(o, "avg_rating")))
            }
        }
        val recent = ArrayList<RRecent>()
        j.optJSONArray("recent")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                recent.add(RRecent(o.optString("epId"), o.optString("epTitle"), o.optString("showId"), o.optString("showName"), o.optString("showArt"), o.optString("feedUrl"), optInt(o, "rating"), o.optString("listenedAt")))
            }
        }
        val rated = ArrayList<RShowRating>()
        j.optJSONArray("showRatings")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                rated.add(RShowRating(o.optString("showId"), o.optString("showName"), o.optString("showArt"), o.optInt("rating")))
            }
        }
        val badges = ArrayList<RBadge>()
        j.optJSONArray("badges")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                badges.add(RBadge(o.optString("id"), o.optInt("tier")))
            }
        }
        return RProfile(
            j.optString("handle"), j.optString("name"), j.optString("bio"), j.optLong("avatarV"),
            j.optInt("followers"), j.optInt("following"), j.optBoolean("isFollowing"), j.optBoolean("isMe"),
            st.optInt("episodes"), st.optDouble("hours", 0.0), optDbl(st, "avgRating"), tops, recent, rated, badges,
        )
    }

    fun groupRatings(base: String, token: String, showId: String): GroupRatings {
        val j = call("GET", base, token, "/v1/shows/" + enc(showId) + "/ratings")
        val eps = HashMap<String, Pair<Double, Int>>()
        j.optJSONObject("episodes")?.let { o ->
            o.keys().forEach { k ->
                val e = o.optJSONObject(k) ?: return@forEach
                val avg = optDbl(e, "avg") ?: return@forEach
                eps[k] = avg to e.optInt("n")
            }
        }
        return GroupRatings(optDbl(j, "showAvg"), j.optInt("showN"), optDbl(j, "epsAvg"), j.optInt("epsN"), eps)
    }
}
