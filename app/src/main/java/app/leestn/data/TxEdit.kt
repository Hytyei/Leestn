package app.leestn.data

/**
 * Manual timing fix for one transcript.
 * [offset] moves every line by that many seconds. [anchors] say "line number N is at second S":
 * the lines in between are spread evenly, so two lines far apart are enough to fix a drifting transcript.
 */
data class TxEdit(val offset: Int = 0, val anchors: Map<Int, Int> = emptyMap()) {
    val isEmpty: Boolean get() = offset == 0 && anchors.isEmpty()
}

object TxEditing {
    /** Applies the edit and returns new lines. Lines the edit can't place keep t = -1. */
    fun apply(lines: List<TxLine>, edit: TxEdit?): List<TxLine> {
        if (edit == null || edit.isEmpty || lines.isEmpty()) return lines
        val n = lines.size
        val allTimed = lines.all { it.t >= 0 }
        // x is where each line sits on the original scale: its time, or its position in the list when it has no time.
        val x = DoubleArray(n) { if (allTimed) lines[it].t.toDouble() else it.toDouble() }
        val anchors = edit.anchors.filterKeys { it in 0 until n }.toSortedMap().map { (i, sec) -> x[i] to sec.toDouble() }
            .let { list ->
                // keep x strictly increasing so the spread never runs backwards
                val out = ArrayList<Pair<Double, Double>>()
                for (a in list) if (out.isEmpty() || a.first > out.last().first) out.add(a)
                out
            }
        val y = DoubleArray(n) { Double.NaN }
        when {
            anchors.isEmpty() -> for (i in 0 until n) if (lines[i].t >= 0) y[i] = lines[i].t.toDouble()
            anchors.size == 1 && allTimed -> {
                val shift = anchors[0].second - anchors[0].first
                for (i in 0 until n) y[i] = x[i] + shift
            }
            anchors.size == 1 -> Unit // untimed text needs two anchors to know the pace
            else -> {
                val a0 = anchors.first()
                val aN = anchors.last()
                val firstSlope = slope(anchors[0], anchors[1])
                val lastSlope = slope(anchors[anchors.size - 2], aN)
                for (i in 0 until n) {
                    val xi = x[i]
                    y[i] = when {
                        xi <= a0.first -> if (allTimed) a0.second + (xi - a0.first) else a0.second + (xi - a0.first) * firstSlope
                        xi >= aN.first -> if (allTimed) aN.second + (xi - aN.first) else aN.second + (xi - aN.first) * lastSlope
                        else -> {
                            var k = 0
                            while (k + 1 < anchors.size - 1 && xi > anchors[k + 1].first) k++
                            val (xa, ya) = anchors[k]
                            val (xb, yb) = anchors[k + 1]
                            ya + (xi - xa) * (yb - ya) / (xb - xa)
                        }
                    }
                }
            }
        }
        var floor = 0.0
        return lines.mapIndexed { i, line ->
            if (y[i].isNaN()) return@mapIndexed line.copy(t = -1)
            val t = maxOf(floor, y[i] + edit.offset)
            floor = t // times never go backwards
            line.copy(t = Math.round(t).toInt())
        }
    }

    /** Slope of a segment, never negative and never zero-division. */
    private fun slope(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first
        return if (dx <= 0.0) 0.0 else maxOf(0.0, (b.second - a.second) / dx)
    }

    private val stampRe = Regex("^\\s*[\\[(]?(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})[\\])]?\\s*[-–—:]?\\s+(.*)$")

    /**
     * Turns pasted text into lines. Understands WebVTT/SRT, lines that start with a time such as "0:12 text" or
     * "[01:02:03] text", and plain text (blank-line separated paragraphs, or one per line).
     */
    fun parsePasted(raw: String): List<TxLine> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return emptyList()
        if (text.contains("-->")) {
            val timed = TranscriptParse.fromTimed(text)
            if (timed.isNotEmpty()) return timed
        }
        val rows = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val stamped = rows.map { stampRe.find(it) }
        if (stamped.count { it != null } * 2 >= rows.size && stamped.any { it != null }) {
            val out = ArrayList<TxLine>()
            for ((i, m) in stamped.withIndex()) {
                if (m != null) {
                    val h = m.groupValues[1].ifEmpty { "0" }.toInt()
                    val t = h * 3600 + m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
                    out.add(TxLine(t, "", m.groupValues[4].trim()))
                } else if (out.isNotEmpty()) { // a continuation line joins the one before it
                    val last = out.removeAt(out.size - 1)
                    out.add(last.copy(text = last.text + " " + rows[i]))
                }
            }
            return out.filter { it.text.isNotBlank() }
        }
        val paras = if (Regex("\n\\s*\n").containsMatchIn(text)) text.split(Regex("\n\\s*\n")) else rows
        return paras.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.map { TxLine(-1, "", it) }
    }
}
