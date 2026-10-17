package app.leestn.data

import java.time.LocalDate

data class PodStat(val pid: String, val min: Int, val n: Int)
data class RatedEp(val ep: Episode, val entry: LogEntry)

class Stats(
    val n: Int,
    val mins: Int,
    val avg: Double,
    val rated: Int,
    val dist: IntArray,
    val pods: List<PodStat>,
    val genres: List<Pair<String, Int>>,
    val months: IntArray,
    val days: Map<String, Int>,
    val bestStreak: Int,
    val weekdays: IntArray,
    val sides: Int,
    val top: List<RatedEp>,
    val year: String,
)

object StatsCalc {
    /** year is "2026" or "all". The month chart always shows one calendar year. */
    fun compute(log: Map<String, LogEntry>, year: String): Stats {
        val rows = ArrayList<RatedEp>()
        for ((id, l) in log) {
            val ep = Catalog.episode(id) ?: continue
            val d = l.d ?: continue
            if (year != "all" && Dates.year(d) != year) continue
            rows.add(RatedEp(ep, l))
        }
        val mins = rows.sumOf { it.ep.min }
        val rated = rows.filter { it.entry.r != null }
        val avg = if (rated.isEmpty()) 0.0 else rated.sumOf { it.entry.r!! }.toDouble() / rated.size
        val dist = IntArray(10)
        rated.forEach { dist[it.entry.r!! - 1]++ }
        val pods = rows.groupBy { it.ep.pid }.map { (pid, l) -> PodStat(pid, l.sumOf { it.ep.min }, l.size) }.sortedByDescending { it.min }
        val genres = rows.groupBy { (Catalog.show(it.ep.pid)?.genre?.ifBlank { null } ?: "Other") }.map { (g, l) -> g to l.sumOf { it.ep.min } }.sortedByDescending { it.second }
        val yr = if (year == "all") Dates.today().substring(0, 4) else year
        val months = IntArray(12)
        rows.forEach { if (Dates.year(it.entry.d!!) == yr) months[Dates.month(it.entry.d)] += it.ep.min }
        val days = HashMap<String, Int>()
        rows.forEach { days[it.entry.d!!] = (days[it.entry.d] ?: 0) + it.ep.min }
        var best = 0
        var cur = 0
        var prev: LocalDate? = null
        days.keys.sorted().forEach { k ->
            val d = LocalDate.parse(k)
            cur = if (prev != null && d.toEpochDay() - prev!!.toEpochDay() == 1L) cur + 1 else 1
            prev = d
            if (cur > best) best = cur
        }
        val wd = IntArray(7)
        rows.forEach { wd[Dates.weekday(it.entry.d!!)] += it.ep.min }
        val sides = rows.count { it.ep.kind == Kind.SIDE }
        val top = rated.sortedWith(compareByDescending<RatedEp> { it.entry.r }.thenByDescending { it.ep.min }).take(5)
        return Stats(rows.size, mins, avg, rated.size, dist, pods, genres, months, days, best, wd, sides, top, yr)
    }

    fun completion(log: Map<String, LogEntry>, p: Podcast): Pair<Int, Int> =
        p.mains.count { log[it.id]?.d != null } to p.mains.size
}
