package io.ymusic.player.mix

object LanguageClassifier {

    private val KEYWORD_PATTERNS = listOf(
        Regex("""(?i)\b(malayalam|mollywood|kerala)\b""") to Language.MALAYALAM,
        Regex("""(?i)\b(tamil|kollywood)\b""") to Language.TAMIL,
        Regex("""(?i)\b(telugu|tollywood)\b""") to Language.TELUGU,
        Regex("""(?i)\b(kannada|sandalwood)\b""") to Language.KANNADA,
        Regex("""(?i)\b(hindi|bollywood)\b""") to Language.HINDI,
        Regex("""(?i)\b(arabic)\b""") to Language.ARABIC,
        Regex("""(?i)\b(english)\b""") to Language.ENGLISH,
    )

    fun classify(
        title: String?,
        artist: String? = null,
        artistStore: LearnedArtistLanguageStore? = null,
    ): Language {
        val combined = listOfNotNull(title, artist).joinToString(" ")
        if (combined.isBlank()) return Language.UNKNOWN

        // 1. Direct Unicode Script Detection (highest priority)
        val scriptLang = detectScript(combined)
        if (scriptLang != null) return scriptLang

        // 2. Keyword detection in title / artist tags
        for ((pattern, lang) in KEYWORD_PATTERNS) {
            if (pattern.containsMatchIn(combined)) {
                return lang
            }
        }

        // 3. Learned artist / channel dictionary
        if (artist != null && artistStore != null) {
            artistStore.getDecisiveLanguage(artist)?.let { return it }
        }

        // 4. Fallback: check if predominantly Latin text
        if (hasLatinScript(combined)) {
            return Language.ENGLISH
        }

        return Language.UNKNOWN
    }

    fun detectScript(text: String): Language? {
        var malayalam = 0
        var tamil = 0
        var telugu = 0
        var kannada = 0
        var devanagari = 0
        var arabic = 0
        var cjk = 0

        for (ch in text) {
            val code = ch.code
            when (code) {
                in 0x0D00..0x0D7F -> malayalam++
                in 0x0B80..0x0BFF -> tamil++
                in 0x0C00..0x0C7F -> telugu++
                in 0x0C80..0x0CFF -> kannada++
                in 0x0900..0x097F -> devanagari++
                in 0x0600..0x06FF, in 0x0750..0x077F, in 0x08A0..0x08FF -> arabic++
                in 0x4E00..0x9FFF, in 0x3040..0x30FF, in 0xAC00..0xD7AF -> cjk++
            }
        }

        val counts = listOf(
            Language.MALAYALAM to malayalam,
            Language.TAMIL to tamil,
            Language.TELUGU to telugu,
            Language.KANNADA to kannada,
            Language.HINDI to devanagari,
            Language.ARABIC to arabic,
            Language.OTHER to cjk,
        ).filter { it.second >= 2 }

        return counts.maxByOrNull { it.second }?.first
    }

    private fun hasLatinScript(text: String): Boolean {
        var latin = 0
        for (ch in text) {
            if (ch in 'A'..'Z' || ch in 'a'..'z') latin++
        }
        return latin >= 3
    }
}
