package app.leestn.data

import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.URLEncoder

/**
 * Ways to bring a show into Leestn besides search.
 *
 * Spotify does not give third-party apps its episode audio, so a Spotify link is resolved to the show's public RSS
 * feed instead: the show name comes from Spotify's open oEmbed endpoint, and the feed from Apple's directory.
 * Shows that only exist inside Spotify (exclusives) have no public feed and cannot be played here.
 */
object Sources {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    fun resolve(input: String): Podcast {
        val text = input.trim()
        if (text.isEmpty()) throw IOException("Paste a link first")
        val lower = text.lowercase()
        return when {
            lower.contains("open.spotify.com/show/") || lower.contains("spotify.com/show/") -> fromSpotify(text)
            lower.contains("open.spotify.com/episode/") -> throw IOException("Paste the link to the show, not a single episode")
            lower.contains("podcasts.apple.com") -> fromApple(text)
            lower.startsWith("http://") || lower.startsWith("https://") -> Podcast(Itunes.idFor(text), "", "", "", "", feedUrl = text)
            else -> throw IOException("That doesn't look like a link")
        }
    }

    private fun fromSpotify(url: String): Podcast {
        val clean = url.substringBefore('?').substringBefore('#')
        val title = try {
            JSONObject(Http.text("https://open.spotify.com/oembed?url=${enc(clean)}", 200_000)).optString("title", "").trim()
        } catch (e: Exception) {
            throw IOException("Couldn't read that Spotify link")
        }
        if (title.isEmpty()) throw IOException("Couldn't read that Spotify link")
        val found = Itunes.search(title, 10)
        val want = norm(title)
        return found.firstOrNull { norm(it.name) == want }
            ?: found.firstOrNull { norm(it.name).contains(want) || want.contains(norm(it.name)) }
            ?: throw IOException("\"$title\" has no public feed, so it can only be played inside Spotify")
    }

    private fun fromApple(url: String): Podcast {
        val id = Regex("id(\\d{5,})").find(url)?.groupValues?.get(1) ?: throw IOException("Couldn't read that Apple Podcasts link")
        return Itunes.lookup(listOf(id)).firstOrNull() ?: throw IOException("That show has no public feed")
    }

    /** Every feed in an OPML export (the format other podcast apps use for subscriptions). */
    fun parseOpml(xml: String): List<Podcast> {
        val out = LinkedHashMap<String, Podcast>()
        val p = Xml.newPullParser()
        p.setInput(xml.byteInputStream(), null)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name.equals("outline", ignoreCase = true)) {
                val feed = (p.getAttributeValue(null, "xmlUrl") ?: p.getAttributeValue(null, "xmlurl"))?.trim().orEmpty()
                if (feed.startsWith("http")) {
                    val name = (p.getAttributeValue(null, "title") ?: p.getAttributeValue(null, "text") ?: "").trim()
                    val id = Itunes.idFor(feed)
                    if (id !in out) out[id] = Podcast(id, name, "", "", "", feedUrl = feed)
                }
            }
            ev = p.next()
        }
        return out.values.toList()
    }
}
