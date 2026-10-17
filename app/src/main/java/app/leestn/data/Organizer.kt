package app.leestn.data

class RawItem(
    val feedIndex: Int,
    val guid: String,
    val title: String,
    val date: String,
    val descHtml: String,
    val audioUrl: String,
    val durationSec: Int,
    val season: Int?,
    val episode: Int?,
    val type: String,
    val imageUrl: String,
    val transcripts: List<Pair<String, String>>,
)

/**
 * Turns a flat RSS item list into the Leestn model:
 *  - full episodes are MAIN, grouped by season (or one "Episodes" group when the feed has no seasons)
 *  - bonus and trailer episodes are SIDE content
 *  - every side episode remembers the main episode it was released after, so the app can say
 *    "listen to this next" or "back to the main story".
 */
object Organizer {
    private val mainCat = Category("main", "Episodes", "", Kind.MAIN)
    private val bonusCat = Category("bonus", "Bonus", "BN", Kind.SIDE)
    private val trailerCat = Category("trailer", "Trailers", "TR", Kind.SIDE)

    fun seasonCat(n: Int) = Category("s$n", "Season $n", "S$n", Kind.MAIN)

    /** Prefers JSON, then WebVTT, then SRT, then plain text. HTML pages are not supported. */
    fun bestTranscript(list: List<Pair<String, String>>): Pair<String, String>? {
        fun rank(type: String): Int {
            val t = type.lowercase()
            return when {
                t.contains("json") -> 0
                t.contains("vtt") -> 1
                t.contains("srt") || t.contains("subrip") -> 2
                t == "text/plain" -> 3
                else -> 99
            }
        }
        return list.filter { it.first.isNotBlank() && rank(it.second) < 99 }.minByOrNull { rank(it.second) }
    }

    class Result(val episodes: List<Episode>, val cats: List<Category>)

    fun build(pid: String, items: List<RawItem>): Result {
        // Feeds sometimes repeat a guid. Episode ids must be unique, so keep the first of each.
        val seen = HashSet<String>()
        val usable = items.filter { it.audioUrl.isNotBlank() && seen.add(it.guid.ifBlank { it.audioUrl }) }
        // oldest first; on the same day the item that appears lower in the (newest first) feed is older
        val sorted = usable.sortedWith(compareBy<RawItem>({ it.date }, { -it.feedIndex }))

        fun isSide(r: RawItem) = r.type == "bonus" || r.type == "trailer"
        val mains = sorted.filter { !isSide(it) }
        val sides = sorted.filter { isSide(it) }

        // category per main item
        val mainGroups = LinkedHashMap<String, MutableList<RawItem>>()
        val catById = HashMap<String, Category>()
        for (r in mains) {
            val c = if (r.season != null && r.season > 0) seasonCat(r.season) else mainCat
            catById[c.id] = c
            mainGroups.getOrPut(c.id) { ArrayList() }.add(r)
        }
        val orderedMainCatIds = mainGroups.keys.sortedBy { id -> mainGroups.getValue(id).first().date }

        val epId = { r: RawItem -> pid + "_" + Text.sha1(r.guid.ifBlank { r.audioUrl }).substring(0, 10) }
        val numberOf = HashMap<String, Int>()
        for (id in orderedMainCatIds) {
            val g = mainGroups.getValue(id)
            val tagged = g.mapNotNull { it.episode }
            val useTags = tagged.size == g.size && tagged.toSet().size == g.size && tagged.all { it > 0 }
            g.forEachIndexed { i, r -> numberOf[epId(r)] = if (useTags) r.episode!! else i + 1 }
        }

        val out = ArrayList<Episode>(usable.size)
        for (id in orderedMainCatIds) {
            val cat = catById.getValue(id)
            for (r in mainGroups.getValue(id)) out.add(make(pid, r, cat, numberOf.getValue(epId(r)), null, epId(r)))
        }
        val mainIdsByDate = mains.map { epId(it) to it.date }
        var bonusN = 0
        var trailerN = 0
        for (r in sides) {
            val cat = if (r.type == "trailer") trailerCat else bonusCat
            val n = if (r.type == "trailer") ++trailerN else ++bonusN
            val after = mainIdsByDate.lastOrNull { it.second <= r.date }?.first
            out.add(make(pid, r, cat, n, after, epId(r)))
        }
        val cats = ArrayList<Category>()
        orderedMainCatIds.forEach { cats.add(catById.getValue(it)) }
        if (sides.any { it.type != "trailer" }) cats.add(bonusCat)
        if (sides.any { it.type == "trailer" }) cats.add(trailerCat)
        return Result(out, cats)
    }

    private fun make(pid: String, r: RawItem, cat: Category, num: Int, after: String?, id: String): Episode {
        val plain = Text.stripHtml(r.descHtml)
        val best = bestTranscript(r.transcripts)
        return Episode(
            id = id, pid = pid, cat = cat, num = num,
            title = r.title.ifBlank { "Untitled episode" },
            durationSec = r.durationSec, date = r.date,
            blurb = Text.blurb(plain), description = plain,
            afterId = after, audioUrl = r.audioUrl, imageUrl = r.imageUrl,
            transcriptUrl = best?.first, transcriptType = best?.second,
        )
    }

    /** What to listen to after this episode: the next main episode plus any side content that fits here. */
    fun nextUp(p: Podcast, ep: Episode): NextUp {
        return if (ep.kind == Kind.MAIN) {
            val i = p.mains.indexOfFirst { it.id == ep.id }
            NextUp(p.mains.getOrNull(i + 1), p.sides.filter { it.afterId == ep.id })
        } else {
            val i = if (ep.afterId == null) -1 else p.mains.indexOfFirst { it.id == ep.afterId }
            NextUp(
                p.mains.getOrNull(i + 1),
                p.sides.filter { it.afterId == ep.afterId && it.id != ep.id && (it.date > ep.date || (it.date == ep.date && it.id > ep.id)) },
            )
        }
    }
}
