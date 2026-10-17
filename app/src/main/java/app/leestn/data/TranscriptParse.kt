package app.leestn.data

/** One timed piece of speech before it is merged into readable paragraphs. */
class Cue(val start: Double, val speaker: String, val text: String)

/** Pure transcript parsing (WebVTT, SRT, plain text). JSON is read in Net.kt because it needs org.json. */
object TranscriptParse {
    private val tsRe = Regex("(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})(?:[.,](\\d{1,3}))?")
    private val voiceRe = Regex("<v(?:\\.[^ >]+)?\\s+([^>]+)>")
    private val tagRe = Regex("<[^>]*>")

    private fun toSeconds(m: MatchResult): Double {
        val h = m.groupValues[1].ifEmpty { "0" }.toInt()
        val mi = m.groupValues[2].toInt()
        val s = m.groupValues[3].toInt()
        val frac = m.groupValues[4]
        val ms = if (frac.isEmpty()) 0.0 else ("0." + frac).toDouble()
        return h * 3600.0 + mi * 60.0 + s + ms
    }

    /** Works for both WebVTT and SRT. */
    fun parseCues(raw: String): List<Cue> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿")
        val cues = ArrayList<Cue>()
        for (block in text.split(Regex("\n{2,}"))) {
            val lines = block.split('\n')
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val m = tsRe.find(lines[ti].substringBefore("-->")) ?: continue
            val start = toSeconds(m)
            val body = lines.drop(ti + 1).joinToString(" ").trim()
            if (body.isEmpty()) continue
            val speaker = voiceRe.find(body)?.groupValues?.get(1)?.trim().orEmpty()
            val clean = Text.decodeEntities(tagRe.replace(body, "")).replace(Regex("\\s+"), " ").trim()
            if (clean.isNotEmpty()) cues.add(Cue(start, speaker, clean))
        }
        return cues
    }

    /**
     * Merge short cues into paragraphs: a new paragraph starts when the speaker changes,
     * the paragraph is older than maxSpan seconds, or there is a long pause.
     */
    fun group(cues: List<Cue>, maxSpan: Double = 20.0): List<TxLine> {
        if (cues.isEmpty()) return emptyList()
        val out = ArrayList<TxLine>()
        var start = cues[0].start
        var last = cues[0].start
        var who = cues[0].speaker
        val sb = StringBuilder(cues[0].text)
        for (i in 1 until cues.size) {
            val c = cues[i]
            val speakerChanged = c.speaker != who && (c.speaker.isNotEmpty() || who.isNotEmpty())
            val tooLong = c.start - start >= maxSpan
            val pause = c.start - last > 6.0
            if (speakerChanged || tooLong || pause) {
                out.add(TxLine(start.toInt(), who, sb.toString()))
                start = c.start
                who = c.speaker
                sb.setLength(0)
                sb.append(c.text)
            } else {
                sb.append(' ').append(c.text)
            }
            last = c.start
        }
        out.add(TxLine(start.toInt(), who, sb.toString()))
        return out
    }

    fun fromTimed(raw: String): List<TxLine> = group(parseCues(raw))

    /** Plain text has no timestamps, so lines get t = -1 and are not synced to playback. */
    fun fromPlain(raw: String): List<TxLine> =
        raw.replace("\r\n", "\n").split(Regex("\n{2,}"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .map { TxLine(-1, "", it) }
}
