package app.leestn.data

/** Compares release versions such as "v1.5.0", "1.5.0" and "1.0.0-pre.2". A pre-release is older than its final release. */
object Version {
    private class Parsed(val nums: List<Int>, val pre: List<String>)

    private fun parse(raw: String): Parsed? {
        val s = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        if (s.isEmpty() || !s[0].isDigit()) return null
        val core = s.substringBefore('-')
        val pre = if (s.contains('-')) s.substringAfter('-').split('.', '-') else emptyList()
        val nums = core.split('.').map { it.toIntOrNull() ?: return null }
        return Parsed(nums, pre)
    }

    /** Positive when [a] is newer than [b]. Unreadable versions count as oldest. */
    fun compare(a: String, b: String): Int {
        val x = parse(a) ?: return if (parse(b) == null) 0 else -1
        val y = parse(b) ?: return 1
        for (i in 0 until maxOf(x.nums.size, y.nums.size)) {
            val d = x.nums.getOrElse(i) { 0 }.compareTo(y.nums.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        if (x.pre.isEmpty() && y.pre.isEmpty()) return 0
        if (x.pre.isEmpty()) return 1
        if (y.pre.isEmpty()) return -1
        for (i in 0 until maxOf(x.pre.size, y.pre.size)) {
            val p = x.pre.getOrNull(i) ?: return -1
            val q = y.pre.getOrNull(i) ?: return 1
            val pn = p.toIntOrNull()
            val qn = q.toIntOrNull()
            val d = when {
                pn != null && qn != null -> pn.compareTo(qn)
                pn != null -> -1
                qn != null -> 1
                else -> p.compareTo(q)
            }
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0
}
