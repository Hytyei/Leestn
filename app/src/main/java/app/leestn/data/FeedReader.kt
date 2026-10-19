package app.leestn.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

class ParsedFeed(
    val title: String,
    val author: String,
    val descHtml: String,
    val imageUrl: String,
    val genre: String,
    val items: List<RawItem>,
    val language: String = "",
)

/**
 * Streaming RSS reader. Understands the iTunes tags (season, episode, episodeType, duration, image)
 * and the Podcasting 2.0 transcript tag. Only uses the plain pull-parser API so large feeds stay cheap.
 */
object FeedReader {
    private const val MAX_ITEMS = 3500

    fun read(input: InputStream): ParsedFeed {
        val p = Xml.newPullParser()
        p.setInput(input, null)

        var depth = 0
        val text = StringBuilder()
        var inItem = false
        var inImage = false
        var language = ""

        var title = ""
        var author = ""
        var desc = ""
        var summary = ""
        var image = ""
        var imageFallback = ""
        var genre = ""

        var iTitle = ""
        var iGuid = ""
        var iDate = ""
        var iDesc = ""
        var iSummary = ""
        var iContent = ""
        var iAudio = ""
        var iDur = ""
        var iSeason: Int? = null
        var iEpisode: Int? = null
        var iType = "full"
        var iImage = ""
        var iTrans = ArrayList<Pair<String, String>>()

        val items = ArrayList<RawItem>()
        var idx = 0

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    depth++
                    text.setLength(0)
                    val n = (p.name ?: "").lowercase()
                    when {
                        n == "item" || n == "entry" -> {
                            inItem = true
                            iTitle = ""; iGuid = ""; iDate = ""; iDesc = ""; iSummary = ""; iContent = ""
                            iAudio = ""; iDur = ""; iSeason = null; iEpisode = null; iType = "full"; iImage = ""
                            iTrans = ArrayList()
                        }
                        n == "image" && !inItem -> inImage = true
                        n == "enclosure" && inItem -> {
                            val url = p.getAttributeValue(null, "url") ?: ""
                            val type = (p.getAttributeValue(null, "type") ?: "").lowercase()
                            val ok = type.isEmpty() || type.startsWith("audio") || type.startsWith("video") || type.contains("mpeg") || type.contains("octet")
                            if (iAudio.isEmpty() && url.isNotBlank() && ok) iAudio = url.trim()
                        }
                        n == "itunes:image" -> {
                            val href = (p.getAttributeValue(null, "href") ?: "").trim()
                            if (inItem) { if (href.isNotEmpty()) iImage = href } else if (href.isNotEmpty() && image.isEmpty()) image = href
                        }
                        n == "itunes:category" && !inItem && genre.isEmpty() -> genre = (p.getAttributeValue(null, "text") ?: "").trim()
                        n == "podcast:transcript" && inItem -> {
                            val url = (p.getAttributeValue(null, "url") ?: "").trim()
                            val type = (p.getAttributeValue(null, "type") ?: "").trim()
                            if (url.isNotEmpty()) iTrans.add(url to type)
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(p.text ?: "")
                XmlPullParser.END_TAG -> {
                    val n = (p.name ?: "").lowercase()
                    val t = text.toString().trim()
                    if (inItem) {
                        when (n) {
                            "title" -> if (depth == 4) iTitle = t
                            "guid", "id" -> if (depth == 4) iGuid = t
                            "pubdate", "published", "dc:date" -> if (iDate.isEmpty()) iDate = t
                            "description" -> if (depth == 4) iDesc = t
                            "itunes:summary" -> iSummary = t
                            "content:encoded" -> iContent = t
                            "itunes:duration" -> iDur = t
                            "itunes:season" -> iSeason = t.toIntOrNull()
                            "itunes:episode" -> iEpisode = t.toIntOrNull()
                            "itunes:episodetype" -> iType = t.lowercase().ifEmpty { "full" }
                            "item", "entry" -> {
                                inItem = false
                                if (items.size < MAX_ITEMS) {
                                    val html = when {
                                        iContent.length > iDesc.length -> iContent
                                        iDesc.isNotEmpty() -> iDesc
                                        else -> iSummary
                                    }
                                    items.add(
                                        RawItem(
                                            feedIndex = idx,
                                            guid = iGuid.ifEmpty { iAudio },
                                            title = iTitle,
                                            date = Text.parseDate(iDate) ?: "1970-01-01",
                                            descHtml = html,
                                            audioUrl = iAudio,
                                            durationSec = Text.parseDuration(iDur),
                                            season = iSeason,
                                            episode = iEpisode,
                                            type = iType,
                                            imageUrl = iImage,
                                            transcripts = iTrans,
                                        )
                                    )
                                }
                                idx++
                            }
                        }
                    } else {
                        when (n) {
                            "image" -> inImage = false
                            "url" -> if (inImage && imageFallback.isEmpty()) imageFallback = t
                            "title" -> if (depth == 3 && !inImage && title.isEmpty()) title = t
                            "itunes:author" -> if (author.isEmpty()) author = t
                            "language" -> if (depth == 3 && language.isEmpty()) language = t
                            "description" -> if (depth == 3) desc = t
                            "itunes:summary" -> if (depth == 3) summary = t
                        }
                    }
                    depth--
                    text.setLength(0)
                }
            }
            ev = p.next()
        }
        return ParsedFeed(
            title = title,
            author = author,
            descHtml = desc.ifEmpty { summary },
            imageUrl = image.ifEmpty { imageFallback },
            genre = genre,
            items = items,
            language = language,
        )
    }
}
