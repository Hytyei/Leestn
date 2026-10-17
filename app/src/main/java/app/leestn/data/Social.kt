package app.leestn.data

class Rng(seed: Int) {
    private var a = seed
    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
    fun int(n: Int): Int = (next() * n).toInt()
}

data class WishEvent(val epId: String, val d: String, val h: Int)
data class ListEvent(val name: String, val count: Int, val pid: String, val d: String, val h: Int)

class Person(
    val id: String,
    val name: String,
    val handle: String,
    val bio: String,
    val hueA: Float,
    val hueB: Float,
    val log: Map<String, LogEntry>,
    val wish: List<WishEvent> = emptyList(),
    val lists: List<ListEvent> = emptyList(),
) {
    val initials: String get() = if (id == "me") "Y" else name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("")
}

/** One entry per show: show id, how many main episodes, how many side episodes. */
data class Pick(val pid: String, val mains: Int, val sides: Int)

object Social {
    fun genLog(seed: Int, picks: List<Pick>): Map<String, LogEntry> {
        val rng = Rng(seed)
        val log = LinkedHashMap<String, LogEntry>()
        for (pk in picks) {
            val p = DemoData.shows.getValue(pk.pid)
            val chosen = (p.mains.take(pk.mains) + p.sides.take(pk.sides)).map { it.id }.toSet()
            var day = rng.int(200)
            for (ep in p.story) {
                if (ep.id !in chosen) continue
                day += 1 + rng.int(8)
                val d = maxOf(day, Dates.idxOf(ep.date) + 1).coerceIn(0, maxOf(0, Dates.todayIdx() - rng.int(4)))
                day = d
                val h = 8 + rng.int(15)
                val r = if (rng.next() < 0.07) null else Math.round(5.6 + rng.next() * 4 + (rng.next() - 0.5)).toInt().coerceIn(3, 10)
                log[ep.id] = LogEntry(Dates.isoOf(d), r, h)
            }
        }
        return log
    }

    private val listNames = listOf("Long drive listens", "Best finales", "Slow burns", "Pre-bed episodes", "Smartest hosts", "Weekend marathons")

    private fun friend(
        id: String, name: String, handle: String, bio: String, hueA: Float, hueB: Float, seed: Int, picks: List<Pick>,
    ): Person {
        val rng = Rng(seed * 7 + 3)
        val log = genLog(seed, picks)
        val rest = DemoData.allEpisodes.filter { it.id !in log }
        val wish = List(4) {
            val ep = rest[rng.int(rest.size)]
            WishEvent(ep.id, Dates.isoOf(Dates.todayIdx() - 1 - rng.int(22)), 8 + rng.int(15))
        }
        val lists = listOf(ListEvent(listNames[seed % listNames.size], 3 + rng.int(6), picks[0].pid, Dates.isoOf(Dates.todayIdx() - rng.int(18)), 8 + rng.int(15)))
        return Person(id, name, handle, bio, hueA, hueB, log, wish, lists)
    }

    val me = Person("me", "You", "@you", "Mock profile. Real accounts arrive with the backend.", 28f, 330f, emptyMap())

    val friends: List<Person> = listOf(
        friend("noor", "Noor Hadid", "@noor", "Audio drama devotee. Will rate your finale.", 330f, 12f, 11,
            listOf(Pick("lsg", 9, 3), Pick("cop", 7, 2), Pick("smh", 4, 1), Pick("fnn", 3, 1))),
        friend("theo", "Theo Lindqvist", "@theo", "Business stories and long drives.", 210f, 170f, 23,
            listOf(Pick("smg", 8, 2), Pick("ps", 6, 2), Pick("cop", 5, 0), Pick("lsg", 3, 0))),
        friend("mei", "Mei Tanabe", "@mei", "Listening at 1.5× since 2019.", 48f, 14f, 37,
            listOf(Pick("ps", 6, 2), Pick("smg", 6, 1), Pick("smh", 4, 2), Pick("fnn", 5, 2), Pick("cop", 4, 1))),
        friend("omar", "Omar Rahimi", "@omar", "True crime, but only the verified kind.", 265f, 300f, 41,
            listOf(Pick("cop", 9, 3), Pick("smh", 2, 0), Pick("lsg", 5, 2))),
        friend("ines", "Inês Carvalho", "@ines", "Travel audio and rain sounds.", 160f, 210f, 53,
            listOf(Pick("fnn", 5, 2), Pick("smh", 5, 2), Pick("lsg", 4, 1))),
        friend("sam", "Sam Whitlock", "@sam", "New here. Recommend me something.", 20f, 300f, 67,
            listOf(Pick("ps", 3, 0), Pick("smg", 2, 0))),
    )
    val friendsById: Map<String, Person> = friends.associateBy { it.id }
    val defaultFollowing = listOf("noor", "theo", "mei", "omar")
}
