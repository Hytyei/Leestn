package app.leestn.data

/** Genres offered at onboarding and in Settings. The search term is what Apple's directory is asked for. */
object Genres {
    val all = listOf(
        "True Crime", "Comedy", "News", "Business", "Technology", "Science", "History", "Society & Culture",
        "Health & Fitness", "Sports", "Education", "Fiction", "Music", "TV & Film", "Arts", "Kids & Family",
        "Religion & Spirituality", "Leisure", "Government", "Self-Improvement",
    )

    fun term(genre: String): String = when (genre) {
        "Society & Culture" -> "society culture"
        "Health & Fitness" -> "health fitness"
        "TV & Film" -> "tv film"
        "Kids & Family" -> "kids family"
        "Religion & Spirituality" -> "spirituality"
        "Fiction" -> "audio drama fiction"
        else -> genre.lowercase()
    }
}
