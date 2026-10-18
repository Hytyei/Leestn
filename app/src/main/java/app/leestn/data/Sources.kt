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
            lower.startsWith("http://") || lower.startsWith("https://") -> {
                val feed = findFeed(text)
                Podcast(Itunes.idFor(feed), "", "", "", "", feedUrl = feed)
            }
            else -> throw IOException("That doesn't look like a link")
        }
    }

    /**
     * The link may be the feed itself or a web page (a podcast website, Castbox, Spotify for Creators…).
     * A page usually advertises its feed in a <link rel="alternate" type="application/rss+xml"> tag, so look for that.
     */
    fun findFeed(url: String): String {
        val body = try { Http.text(url, 3_000_000) } catch (e: Exception) { throw IOException("Couldn't open that link (${e.message})") }
        val head = body.take(2000).lowercase()
        if (head.contains("<rss") || head.contains("<feed") || head.contains("<?xml")) return url
        val tag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(body).map { it.value }.firstOrNull {
            it.contains("alternate", true) && (it.contains("application/rss+xml", true) || it.contains("application/atom+xml", true))
        }
        val href = tag?.let { Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?.replace("&amp;", "&")
        if (href != null) return try { java.net.URI(url).resolve(href).toString() } catch (e: Exception) { href }
        throw IOException("That page doesn't point to a podcast feed. Paste the RSS feed link instead. In Spotify for Creators it is under Settings → Distribution → RSS.")
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
            ?: throw IOException("\"$title\" isn't in Apple's directory, so Leestn can't find its feed from a Spotify link. If it's your own show, paste its RSS feed link instead (Spotify for Creators → Settings → Distribution → RSS). Any link works, not just Spotify's.")
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
