package io.ymusic.player.mix

object MoodClassifier {

    private val CALM_PATTERN = Regex(
        """(?i)\b(acoustic|lofi|lo-fi|ambient|slow|slowed|piano|sleep|calm|relaxing|chill|peaceful|soft|meditation|soothing|unplugged|lullaby)\b"""
    )

    private val ENERGETIC_PATTERN = Regex(
        """(?i)\b(remix|trap|metal|rock|fast|dance|hype|bass boost|bass boosted|edm|party|club|hardstyle|electronic|upbeat|workout|dj|club mix)\b"""
    )

    fun classify(title: String?, artist: String? = null): Mood {
        val combined = listOfNotNull(title, artist).joinToString(" ")
        if (combined.isBlank()) return Mood.UNKNOWN

        val isEnergetic = ENERGETIC_PATTERN.containsMatchIn(combined)
        val isCalm = CALM_PATTERN.containsMatchIn(combined)

        return when {
            isEnergetic && !isCalm -> Mood.ENERGETIC
            isCalm && !isEnergetic -> Mood.CALM
            else -> Mood.UNKNOWN
        }
    }
}
