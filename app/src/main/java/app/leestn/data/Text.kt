package app.leestn.data

import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure helpers for feed text, durations and dates. No Android dependencies, so they can be unit tested on the JVM. */
object Text {
    fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in d) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private val entityRe = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘",
        "rdquo" to "”", "ldquo" to "“", "copy" to "©", "eacute" to "é",
    )

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        return entityRe.replace(s) { m ->
            val g = m.groupValues[1]
            when {
                g.startsWith("#x") || g.startsWith("#X") -> g.substring(2).toIntOrNull(16)?.let { cp -> codePoint(cp) } ?: m.value
                g.startsWith("#") -> g.substring(1).toIntOrNull()?.let { cp -> codePoint(cp) } ?: m.value
                else -> named[g] ?: m.value
            }
        }
    }

    private fun codePoint(cp: Int): String =
        if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else ""

    private val brRe = Regex("(?i)<br\\s*/?>")
    private val blockEndRe = Regex("(?i)</(p|div|li|h[1-6]|ul|ol|blockquote)>")
    private val liStartRe = Regex("(?i)<li[^>]*>")
    private val tagRe = Regex("<[^>]*>")
    private val spacesRe = Regex("[ \\t\\u00A0]+")
    private val manyNlRe = Regex("\\n{3,}")

    /** Show notes are HTML. Turn them into readable plain text with paragraph breaks. */
    fun stripHtml(html: String): String {
        if (html.isEmpty()) return ""
        var s = html
        s = brRe.replace(s, "\n")
        s = liStartRe.replace(s, "\n• ")
        s = blockEndRe.replace(s, "\n\n")
        s = tagRe.replace(s, "")
        s = decodeEntities(s)
        s = spacesRe.replace(s, " ")
        s = s.lines().joinToString("\n") { it.trim() }
        s = manyNlRe.replace(s, "\n\n")
        return s.trim()
    }

    /** One short line for list rows. */
    fun blurb(plain: String, max: Int = 140): String {
        val oneLine = plain.replace(Regex("\\s+"), " ").trim()
        if (oneLine.length <= max) return oneLine
        val cut = oneLine.substring(0, max)
        val sp = cut.lastIndexOf(' ')
        return (if (sp > max / 2) cut.substring(0, sp) else cut).trimEnd(',', ';', ':', '-', ' ') + "…"
    }

    fun tagline(plain: String, max: Int = 160): String {
        val oneLine = plain.replace(Regex("\\s+"), " ").trim()
        if (oneLine.length <= max) return oneLine
        val end = oneLine.indexOf(". ").let { if (it in 40..max) it + 1 else -1 }
        return if (end > 0) oneLine.substring(0, end) else blurb(oneLine, max)
    }

    /** itunes:duration is seconds ("3723"), mm:ss, or hh:mm:ss, sometimes with decimals. Returns 0 when unknown. */
    fun parseDuration(raw: String?): Int {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0
        val v: Double = if (s.contains(':')) {
            val parts = s.split(':').map { it.trim().toDoubleOrNull() ?: return 0 }
            var total = 0.0
            for (p in parts) total = total * 60 + p
            total
        } else {
            s.toDoubleOrNull() ?: return 0
        }
        return if (v.isNaN() || v < 0 || v > 60 * 60 * 48) 0 else v.toInt()
    }

    private val leadingDow = Regex("^[A-Za-z]{3,9},?\\s+")
    private val patterns: List<DateTimeFormatter> = listOf(
        "d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss z", "d MMM yyyy HH:mm Z", "d MMM yyyy HH:mm z",
        "d MMM yyyy HH:mm:ss", "d MMM yyyy HH:mm", "d MMMM yyyy HH:mm:ss Z", "d MMMM yyyy", "d MMM yyyy",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    /** RSS pubDate (RFC 822 in many flavours) or ISO 8601 to yyyy-MM-dd. Returns null when unreadable. */
    fun parseDate(raw: String?): String? {
        var s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (s.length >= 10 && s[4] == '-' && s[7] == '-') {
            return try { LocalDate.parse(s.substring(0, 10)).toString() } catch (e: Exception) { null }
        }
        s = leadingDow.replace(s, "")
        s = s.replace(Regex("\\s+"), " ")
        s = s.replace(Regex("\\b(UTC|UT)$"), "GMT").replace("Sept ", "Sep ")
        for (f in patterns) {
            try {
                return LocalDate.from(f.parse(s)).toString()
            } catch (e: Exception) {
                // try the next pattern
            }
        }
        return null
    }
}
