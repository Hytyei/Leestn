package app.leestn.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.io.IOException

/**
 * In-memory index of every podcast and episode the app knows about, plus the on-disk feed cache.
 * Reads subscribe the calling composable to updates through [version]. All mutation happens on the main thread;
 * the blocking [fetch] and [loadCached] calls only read and write files and must run on a background dispatcher.
 */
object Catalog {
    private val version = mutableIntStateOf(0)
    private val shows = HashMap<String, Podcast>()
    private val episodes = HashMap<String, Episode>()
    private var dir: File? = null
    private var app: Context? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val d = File(ctx.filesDir, "feeds")
        d.mkdirs()
        dir = d
    }

    fun appContext(): Context = app ?: error("Catalog not initialised")

    fun show(id: String): Podcast? {
        version.intValue
        return shows[id]
    }

    fun episode(id: String): Episode? {
        version.intValue
        return episodes[id]
    }

    fun nextUp(ep: Episode): NextUp {
        val p = show(ep.pid) ?: return NextUp(null, emptyList())
        return Organizer.nextUp(p, ep)
    }

    /** Remember a show. A stub without episodes never replaces a show that is already loaded. */
    fun register(p: Podcast) {
        val cur = shows[p.id]
        if (cur != null && p.eps.isEmpty() && cur.eps.isNotEmpty()) return
        shows[p.id] = p
        for (e in p.eps) episodes[e.id] = e
        version.intValue = version.intValue + 1
    }

    private fun feedFile(id: String) = File(dir, "$id.xml")
    private fun urlFile(id: String) = File(dir, "$id.url")

    /** Download, parse and cache a feed. Throws on network or format errors and leaves the old cache untouched. */
    fun fetch(hint: Podcast): Podcast {
        val d = dir ?: throw IllegalStateException("Catalog not initialised")
        val tmp = File(d, hint.id + ".tmp")
        try {
            Http.download(hint.feedUrl, tmp)
            val parsed = tmp.inputStream().use { FeedReader.read(it) }
            if (parsed.items.none { it.audioUrl.isNotBlank() }) throw IOException("No playable episodes in this feed")
            feedFile(hint.id).delete()
            tmp.renameTo(feedFile(hint.id))
            urlFile(hint.id).writeText(hint.feedUrl)
            return assemble(hint, parsed)
        } finally {
            tmp.delete()
        }
    }

    /** Rebuild shows from the on-disk cache. Ids with no cached feed are skipped. */
    fun loadCached(ids: Collection<String>): List<Podcast> {
        val out = ArrayList<Podcast>()
        for (id in ids) {
            val f = feedFile(id)
            val u = urlFile(id)
            if (!f.exists() || !u.exists()) continue
            try {
                val hint = Podcast(id = id, name = "", host = "", genre = "", tagline = "", feedUrl = u.readText().trim())
                val parsed = f.inputStream().use { FeedReader.read(it) }
                out.add(assemble(hint, parsed))
            } catch (e: Exception) {
                // a corrupt cache file is simply ignored and fetched again later
            }
        }
        return out
    }

    private fun assemble(hint: Podcast, parsed: ParsedFeed): Podcast {
        val res = Organizer.build(hint.id, parsed.items)
        val plain = Text.stripHtml(parsed.descHtml)
        return Podcast(
            id = hint.id,
            name = parsed.title.ifBlank { hint.name }.ifBlank { "Podcast" },
            host = parsed.author.ifBlank { hint.host },
            genre = hint.genre.ifBlank { parsed.genre },
            tagline = Text.tagline(plain).ifBlank { hint.tagline },
            feedUrl = hint.feedUrl,
            artworkUrl = hint.artworkUrl.ifBlank { parsed.imageUrl },
            eps = res.episodes,
            cats = res.cats,
            description = plain.ifBlank { hint.description },
        )
    }
}
