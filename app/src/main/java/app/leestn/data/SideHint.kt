package app.leestn.data

/** Guesses which episodes are side content (trailers, teasers, bonus clips) from the title. Only a hint: the listener decides. */
object SideHint {
    private val words = Regex("""\b(trailer|teaser|preview|promo|bonus|announcement|coming soon|season \d+ (?:is coming|announcement)|behind the scenes|outtakes?|q\s*&\s*a|ama)\b""", RegexOption.IGNORE_CASE)
    fun looksSide(title: String): Boolean = words.containsMatchIn(title)
}
