package app.leestn.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Kind { MAIN, SIDE }
enum class Motif { ARCS, BARS, MOON, BEAM, WAVES, PIXELS }

data class Category(val id: String, val name: String, val short: String, val kind: Kind)

data class Episode(
    val id: String,
    val pid: String,
    val cat: Category,
    val num: Int,
    val title: String,
    val durationSec: Int,
    val date: String,
    val blurb: String,
    val description: String,
    val afterId: String?,
    val audioUrl: String,
    val imageUrl: String = "",
    val transcriptUrl: String? = null,
    val transcriptType: String? = null,
) {
    val kind: Kind get() = cat.kind
    val seconds: Int get() = durationSec

    /** Whole minutes, rounded. 0 when the feed gives no duration. */
    val min: Int get() = (durationSec + 30) / 60

    /** S2E5 for seasoned main episodes, E12 for unseasoned ones, BN3 / TR1 for side content. */
    val code: String
        get() = if (kind == Kind.MAIN) {
            if (cat.short.isEmpty()) "E$num" else "${cat.short}E$num"
        } else {
            "${cat.short}$num"
        }
}

private fun hueFor(id: String, salt: Int): Float {
    val h = (id.hashCode() * 31 + salt * 7919) and 0x7fffffff
    return (h % 360).toFloat()
}

class Podcast(
    val id: String,
    val name: String,
    val host: String,
    val genre: String,
    val tagline: String,
    val feedUrl: String = "",
    val artworkUrl: String = "",
    val eps: List<Episode> = emptyList(),
    cats: List<Category>? = null,
    hueA: Float? = null,
    hueB: Float? = null,
    motif: Motif? = null,
    val description: String = tagline,
) {
    val hueA: Float = hueA ?: hueFor(id, 1)
    val hueB: Float = hueB ?: hueFor(id, 2)
    val motif: Motif = motif ?: Motif.entries[(id.hashCode() and 0x7fffffff) % Motif.entries.size]

    val cats: List<Category> = cats ?: eps.map { it.cat }.distinct()
    val loaded: Boolean get() = eps.isNotEmpty()

    val mains: List<Episode> = eps.filter { it.kind == Kind.MAIN }
    val sides: List<Episode> = eps.filter { it.kind == Kind.SIDE }

    /** Main episodes in order, each followed by the side content that fits right after it. */
    val story: List<Episode> = run {
        val byAfter = sides.groupBy { it.afterId }
        val out = ArrayList<Episode>(eps.size)
        byAfter[null]?.let { list -> out.addAll(list.sortedBy { e -> e.date }) }
        for (m in mains) {
            out.add(m)
            byAfter[m.id]?.let { list -> out.addAll(list.sortedBy { e -> e.date }) }
        }
        out
    }
}

data class LogEntry(val d: String?, val r: Int?, val h: Int = 20)

/** t is seconds from the start, or -1 when the transcript has no timestamps. */
data class TxLine(val t: Int, val who: String, val text: String)

data class NextUp(val nextMain: Episode?, val side: List<Episode>)

sealed class ListItem {
    abstract val id: String
    data class Ep(override val id: String) : ListItem()
    data class Show(override val id: String) : ListItem()
}

object Dates {
    private val base: LocalDate = LocalDate.of(2026, 1, 1)
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

    fun today(): String = LocalDate.now().toString()
    fun idxOf(s: String): Int = (LocalDate.parse(s).toEpochDay() - base.toEpochDay()).toInt()
    fun isoOf(i: Int): String = base.plusDays(i.toLong()).toString()
    fun todayIdx(): Int = idxOf(today())

    fun full(s: String): String = LocalDate.parse(s).format(fullFmt)
    fun short(s: String): String = LocalDate.parse(s).format(shortFmt)
    fun year(s: String): String = s.substring(0, 4)
    fun month(s: String): Int = s.substring(5, 7).toInt() - 1
    fun weekday(s: String): Int = LocalDate.parse(s).dayOfWeek.value - 1 // Monday = 0

    fun rel(s: String): String {
        val d = todayIdx() - idxOf(s)
        return when {
            d <= 0 -> "today"
            d == 1 -> "yesterday"
            d < 7 -> "${d}d ago"
            d < 31 -> "${d / 7}w ago"
            else -> if (year(s) == today().substring(0, 4)) short(s) else full(s)
        }
    }

    fun dur(min: Int): String =
        if (min <= 0) "–" else if (min >= 60) "${min / 60} h" + (if (min % 60 != 0) " ${min % 60} min" else "") else "$min min"

    fun hours(min: Int): String = String.format(Locale.US, "%.1f h", min / 60.0)

    fun clock(sec: Int): String {
        val s0 = if (sec < 0) 0 else sec
        val h = s0 / 3600
        val m = (s0 % 3600) / 60
        val s = s0 % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }
}
