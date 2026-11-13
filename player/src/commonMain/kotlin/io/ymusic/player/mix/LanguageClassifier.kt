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
        contextLanguage: Language? = null,
    ): Language {
        val combined = listOfNotNull(title, artist).joinToString(" ").lowercase()
        if (combined.isBlank()) return contextLanguage ?: Language.UNKNOWN

        // 1. Direct Unicode Script Detection (highest priority - STRONG = 2)
        val scriptLang = detectScript(combined)
        if (scriptLang != null) return scriptLang

        // 2. Explicit Keyword tags (e.g. "[Malayalam Song]", "(Tamil Video)") - STRONG = 2
        for ((pattern, lang) in KEYWORD_PATTERNS) {
            if (pattern.containsMatchIn(combined)) {
                return lang
            }
        }

        // 3. Learned artist / channel dictionary (STRONG = 2 if decisive)
        if (artist != null && artistStore != null) {
            artistStore.getDecisiveLanguage(artist)?.let { return it }
        }

        // 4. Evidence scoring for Indic languages (Strong = 2, Weak = 1, Threshold >= 2)
        val scores = mutableMapOf<Language, Int>()

        // Check Exclusive Channels / Labels (Strong = 2)
        LanguageStarterData.EXCLUSIVE_LABELS.forEach { (lang, labels) ->
            if (labels.any { combined.contains(it) }) {
                scores[lang] = (scores[lang] ?: 0) + 2
            }
        }

        // Check Strong Vocabulary (Strong = 2)
        LanguageStarterData.STRONG_VOCABULARY.forEach { (lang, words) ->
            if (words.any { word -> Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(combined) }) {
                scores[lang] = (scores[lang] ?: 0) + 2
            }
        }

        // Check Multilingual Artists (Weak = 1)
        LanguageStarterData.MULTILINGUAL_ARTISTS.forEach { (lang, artists) ->
            if (artists.any { art -> combined.contains(art) }) {
                scores[lang] = (scores[lang] ?: 0) + 1
            }
        }

        // Check Shared Vocabulary (Weak = 1)
        LanguageStarterData.SHARED_VOCABULARY.forEach { (lang, words) ->
            if (words.any { word -> Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(combined) }) {
                scores[lang] = (scores[lang] ?: 0) + 1
            }
        }

        // Best scoring Indic language with score >= 2 wins
        val bestIndic = scores.filter { it.value >= 2 }.maxByOrNull { it.value }?.key
        if (bestIndic != null) {
            return bestIndic
        }

        // If score is 1 (single weak signal) and contextLanguage matches that language:
        if (contextLanguage != null && (scores[contextLanguage] ?: 0) >= 1) {
            return contextLanguage
        }

        // 5. Positive English Detection (words or recognized English artists):
        val hasEnglishWord = LanguageStarterData.ENGLISH_POSITIVE_WORDS.any { word ->
            Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(combined)
        }
        val hasEnglishArtist = LanguageStarterData.ENGLISH_ARTISTS.any { art ->
            combined.contains(art)
        }
        if (hasEnglishWord || hasEnglishArtist) {
            return Language.ENGLISH
        }

        // 6. Inherit contextLanguage if available and non-unknown
        if (contextLanguage != null && contextLanguage != Language.UNKNOWN) {
            return contextLanguage
        }

        // 7. Latin script with no evidence is UNKNOWN (NEVER default to English!)
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
                in 0x0600..0x06FF -> arabic++
                in 0x4E00..0x9FFF, in 0x3040..0x30FF, in 0xAC00..0xD7AF -> cjk++
            }
        }

        val max = maxOf(malayalam, tamil, telugu, kannada, devanagari, arabic, cjk)
        if (max < 3) return null

        return when (max) {
            malayalam -> Language.MALAYALAM
            tamil -> Language.TAMIL
            telugu -> Language.TELUGU
            kannada -> Language.KANNADA
            devanagari -> Language.HINDI
            arabic -> Language.ARABIC
            else -> null
        }
    }

    fun hasLatinScript(text: String): Boolean {
        var count = 0
        for (ch in text) {
            val code = ch.code
            if ((code in 0x0041..0x005A) || (code in 0x0061..0x007A)) {
                count++
                if (count >= 3) return true
            }
        }
        return false
    }
}
