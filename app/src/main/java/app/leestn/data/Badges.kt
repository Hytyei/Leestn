package app.leestn.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A badge with several tiers. [goals] are the metric values needed for tier 1, 2, 3 and so on.
 * Every badge is computed from the listening log, so it works offline and for any profile.
 */
class BadgeDef(
    val id: String,
    val name: String,
    val desc: String,
    val unit: String,
    val goals: List<Int>,
    val metric: (BadgeStats) -> Int,
)

class BadgeProgress(val def: BadgeDef, val value: Int) {
    /** 0 means locked, otherwise the number of goals reached. */
    val tier: Int = def.goals.count { value >= it }
    val maxed: Boolean get() = tier >= def.goals.size
    val nextGoal: Int? get() = def.goals.getOrNull(tier)
    val earned: Boolean get() = tier > 0

    /** 0..1 progress towards the next tier (or 1 when maxed). */
    val fraction: Float
        get() {
            val next = nextGoal ?: return 1f
            val prev = if (tier == 0) 0 else def.goals[tier - 1]
            return ((value - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
        }

    /** Unique per badge and tier, used to detect newly unlocked tiers. */
    fun key(t: Int = tier) = "${def.id}:$t"
}

/** Everything the badge rules look at, derived once from the log. */
class BadgeStats(
    val episodes: Int,
    val hours: Int,
    val bestStreak: Int,
    val night: Int,
    val morning: Int,
    val bestDayEpisodes: Int,
    val bestDayHours: Int,
    val ratings: Int,
    val tens: Int,
    val toughCrowd: Int,
    val easyToPlease: Int,
    val sides: Int,
    val shows: Int,
    val genres: Int,
    val loyal: Int,
    val completed: Int,
    val fresh: Int,
    val archive: Int,
    val activeMonths: Int,
)

object Badges {
    val defs: List<BadgeDef> = listOf(
        BadgeDef("listener", "Listener", "Episodes listened to", "episodes", listOf(1, 10, 50, 150, 500)) { it.episodes },
        BadgeDef("hours", "Hours Logged", "Total time listened", "hours", listOf(5, 25, 100, 300, 1000)) { it.hours },
        BadgeDef("streak", "On a Roll", "Longest streak of listening days", "days", listOf(3, 7, 14, 30, 100)) { it.bestStreak },
        BadgeDef("night", "Night Owl", "Listens logged between 11 pm and 5 am", "listens", listOf(5, 20, 60)) { it.night },
        BadgeDef("morning", "Early Bird", "Listens logged between 5 am and 8 am", "listens", listOf(5, 20, 60)) { it.morning },
        BadgeDef("binge", "Binge Mode", "Most episodes in a single day", "episodes", listOf(3, 5, 8)) { it.bestDayEpisodes },
        BadgeDef("marathon", "Marathoner", "Most hours in a single day", "hours", listOf(3, 6, 10)) { it.bestDayHours },
        BadgeDef("critic", "Critic", "Episodes rated", "ratings", listOf(5, 25, 100, 300)) { it.ratings },
        BadgeDef("ten", "Perfect Ten", "Episodes rated 10 out of 10", "tens", listOf(1, 5, 20)) { it.tens },
        BadgeDef("tough", "Tough Crowd", "Average rating of 6 or lower across 20+ ratings", "", listOf(1)) { it.toughCrowd },
        BadgeDef("easy", "Easy to Please", "Average rating of 8.5 or higher across 20+ ratings", "", listOf(1)) { it.easyToPlease },
        BadgeDef("side", "Side Quester", "Bonus and side episodes listened to", "side episodes", listOf(1, 10, 40)) { it.sides },
        BadgeDef("explorer", "Explorer", "Different shows listened to", "shows", listOf(3, 10, 25, 60)) { it.shows },
        BadgeDef("genres", "Genre Hopper", "Different genres listened to", "genres", listOf(3, 6, 10)) { it.genres },
        BadgeDef("loyal", "Loyal Listener", "Most episodes of a single show", "episodes", listOf(10, 25, 75, 200)) { it.loyal },
        BadgeDef("complete", "Completionist", "Shows finished from first main episode to last", "shows", listOf(1, 3, 10)) { it.completed },
        BadgeDef("fresh", "Fresh Ears", "Episodes heard within 2 days of release", "episodes", listOf(5, 20, 60)) { it.fresh },
        BadgeDef("archive", "Archivist", "Episodes heard 3+ years after release", "episodes", listOf(5, 20, 60)) { it.archive },
        BadgeDef("regular", "Regular", "Different months with a listen this year", "months", listOf(3, 6, 12)) { it.activeMonths },
    )

    /** One listened episode with what the rules need to know about it. */
    class Row(val ep: Episode, val entry: LogEntry, val genre: String)

    /**
     * [log] is the whole log. [episode] and [genre] look things up (episodes that aren't known are skipped),
     * [mainsOf] returns a show's main episodes for the completion rule, and [today] is an ISO date.
     */
    fun stats(
        log: Map<String, LogEntry>,
        episode: (String) -> Episode?,
        genre: (String) -> String,
        mainsOf: (String) -> List<Episode>,
        today: String,
    ): BadgeStats {
        val rows = ArrayList<Row>()
        for ((id, l) in log) {
            if (l.d == null) continue
            val ep = episode(id) ?: continue
            rows.add(Row(ep, l, genre(ep.pid)))
        }
        val byDay = rows.groupBy { it.entry.d!! }
        var best = 0
        var cur = 0
        var prev: LocalDate? = null
        for (k in byDay.keys.sorted()) {
            val d = LocalDate.parse(k)
            cur = if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1L) cur + 1 else 1
            prev = d
            if (cur > best) best = cur
        }
        val rated = rows.mapNotNull { it.entry.r }
        val avg = if (rated.isEmpty()) 0.0 else rated.average()
        val perShow = rows.groupBy { it.ep.pid }
        val year = today.substring(0, 4)
        var completed = 0
        for (pid in perShow.keys) {
            val mains = mainsOf(pid)
            if (mains.size >= 5 && mains.all { m -> log[m.id]?.d != null }) completed++
        }
        return BadgeStats(
            episodes = rows.size,
            hours = rows.sumOf { it.ep.min } / 60,
            bestStreak = best,
            night = rows.count { it.entry.h >= 23 || it.entry.h < 5 },
            morning = rows.count { it.entry.h in 5..7 },
            bestDayEpisodes = byDay.values.maxOfOrNull { it.size } ?: 0,
            bestDayHours = (byDay.values.maxOfOrNull { d -> d.sumOf { it.ep.min } } ?: 0) / 60,
            ratings = rated.size,
            tens = rated.count { it == 10 },
            toughCrowd = if (rated.size >= 20 && avg <= 6.0) 1 else 0,
            easyToPlease = if (rated.size >= 20 && avg >= 8.5) 1 else 0,
            sides = rows.count { it.ep.kind == Kind.SIDE },
            shows = perShow.size,
            genres = rows.map { it.genre }.filter { it.isNotBlank() }.distinct().size,
            loyal = perShow.values.maxOfOrNull { it.size } ?: 0,
            completed = completed,
            fresh = rows.count { r -> safeDays(r.ep.date, r.entry.d!!) in 0..2 },
            archive = rows.count { r -> safeDays(r.ep.date, r.entry.d!!) >= 365 * 3 },
            activeMonths = rows.map { it.entry.d!! }.filter { it.startsWith(year) }.map { it.substring(0, 7) }.distinct().size,
        )
    }

    private fun safeDays(from: String, to: String): Long = try {
        ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to))
    } catch (e: Exception) { -1 }

    fun compute(st: BadgeStats): List<BadgeProgress> = defs.map { BadgeProgress(it, it.metric(st)) }
}
