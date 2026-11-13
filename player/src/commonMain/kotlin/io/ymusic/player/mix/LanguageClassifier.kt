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

    data class EvidenceResult(
        val language: Language,
        val signals: List<Pair<String, Int>>,
    )

    fun classify(
        title: String?,
        artist: String? = null,
        artistStore: LearnedArtistLanguageStore? = null,
        contextLanguage: Language? = null,
    ): Language = classifyWithEvidence(title, artist, null, artistStore, contextLanguage).language

    fun classifyWithEvidence(
        title: String?,
        artist: String? = null,
        channel: String? = null,
        artistStore: LearnedArtistLanguageStore? = null,
        contextLanguage: Language? = null,
    ): EvidenceResult {
        val combined = listOfNotNull(title, artist, channel).joinToString(" ").lowercase()
        val collectedSignals = mutableListOf<Pair<String, Int>>()
        if (combined.isBlank()) {
            return EvidenceResult(contextLanguage ?: Language.UNKNOWN, collectedSignals)
        }

        // 1. Direct Unicode Script Detection (highest priority - STRONG = 2)
        val scriptLang = detectScript(combined)
        if (scriptLang != null) {
            collectedSignals += "Native script ($scriptLang)" to 2
            return EvidenceResult(scriptLang, collectedSignals)
        }

        // 2. Explicit Keyword tags (e.g. "[Malayalam Song]", "(Tamil Video)") - STRONG = 2
        for ((pattern, lang) in KEYWORD_PATTERNS) {
            val match = pattern.find(combined)
            if (match != null) {
                collectedSignals += "Keyword tag '${match.value}' ($lang)" to 2
                return EvidenceResult(lang, collectedSignals)
            }
        }

        // 3. Learned artist / channel dictionary (STRONG = 2 if decisive)
        if (artist != null && artistStore != null) {
            val learned = artistStore.getDecisiveLanguage(artist)
            if (learned != null) {
                collectedSignals += "Learned artist '$artist' ($learned)" to 2
                return EvidenceResult(learned, collectedSignals)
            }
        }

        // 4. Evidence scoring for Indic languages (Strong = 2, Weak = 1, Threshold >= 2)
        val scores = mutableMapOf<Language, Int>()

        // Check Exclusive Channels / Labels (Strong = 2)
        LanguageStarterData.EXCLUSIVE_LABELS.forEach { (lang, labels) ->
            labels.forEach { label ->
                if (combined.contains(label)) {
                    scores[lang] = (scores[lang] ?: 0) + 2
                    collectedSignals += "Exclusive label '$label' ($lang)" to 2
                }
            }
        }

        // Check Multi-Language Publishers / Labels (Weak = 1)
        LanguageStarterData.WEAK_LABELS.forEach { (lang, labels) ->
            labels.forEach { label ->
                if (combined.contains(label)) {
                    scores[lang] = (scores[lang] ?: 0) + 1
                    collectedSignals += "Publisher '$label' ($lang)" to 1
                }
            }
        }

        // Check Strong Vocabulary (Strong = 2)
        LanguageStarterData.STRONG_VOCABULARY.forEach { (lang, words) ->
            words.forEach { word ->
                if (Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(combined)) {
                    scores[lang] = (scores[lang] ?: 0) + 2
                    collectedSignals += "Strong word '$word' ($lang)" to 2
                }
            }
        }

        // Check Multilingual Artists (Weak = 1)
        LanguageStarterData.MULTILINGUAL_ARTISTS.forEach { (lang, artists) ->
            artists.forEach { art ->
                if (combined.contains(art)) {
                    scores[lang] = (scores[lang] ?: 0) + 1
                    collectedSignals += "Multilingual artist '$art' ($lang)" to 1
                }
            }
        }

        // Check Shared Vocabulary (Weak = 1)
        LanguageStarterData.SHARED_VOCABULARY.forEach { (lang, words) ->
            words.forEach { word ->
                if (Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(combined)) {
                    scores[lang] = (scores[lang] ?: 0) + 1
                    collectedSignals += "Shared word '$word' ($lang)" to 1
                }
            }
        }

        // Best scoring Indic language with score >= 2 wins
        val bestIndic = scores.filter { it.value >= 2 }.maxByOrNull { it.value }?.key
        if (bestIndic != null) {
            return EvidenceResult(bestIndic, collectedSignals)
        }

        // If score is 1 (single weak signal) and contextLanguage matches that language:
        if (contextLanguage != null && (scores[contextLanguage] ?: 0) >= 1) {
            collectedSignals += "Context match with weak signal ($contextLanguage)" to 1
            return EvidenceResult(contextLanguage, collectedSignals)
        }

        // 5. Positive English Detection (words or recognized English artists):
        val matchedEnglishWords = LanguageStarterData.ENGLISH_POSITIVE_WORDS.filter { word ->
            Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(combined)
        }
        val matchedEnglishArtists = LanguageStarterData.ENGLISH_ARTISTS.filter { art ->
            combined.contains(art)
        }
        if (matchedEnglishWords.isNotEmpty() || matchedEnglishArtists.isNotEmpty()) {
            matchedEnglishWords.forEach { collectedSignals += "English word '$it'" to 2 }
            matchedEnglishArtists.forEach { collectedSignals += "English artist '$it'" to 2 }
            return EvidenceResult(Language.ENGLISH, collectedSignals)
        }

        // 6. Inherit contextLanguage if available and non-unknown
        if (contextLanguage != null && contextLanguage != Language.UNKNOWN) {
            collectedSignals += "Context inheritance ($contextLanguage)" to 0
            return EvidenceResult(contextLanguage, collectedSignals)
        }

        // 7. Latin script with no evidence is UNKNOWN (NEVER default to English!)
        return EvidenceResult(Language.UNKNOWN, collectedSignals)
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
