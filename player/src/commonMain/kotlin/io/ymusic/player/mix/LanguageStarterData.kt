package io.ymusic.player.mix

/**
 * STARTER DATA for user review.
 * Backing resource: player/src/commonMain/resources/mix/language_starter_data.json
 *
 * Evidence scoring rule:
 * - Strong signal (score = 2):
 *     1. Native Unicode script (Malayalam, Tamil, Telugu, Devanagari, Arabic)
 *     2. Decisive learned channel / artist from store (>=70% with >=3 observations)
 *     3. Single-language exclusive record label or channel
 *     4. Unique language vocabulary (strong words)
 *     5. Channel keyword tags (e.g. "Malayalam", "Tamil", "Hindi" in channel name)
 * - Weak signal (score = 1):
 *     1. Multilingual artists (aliases grouped, max 1 pt per artist entity)
 *     2. Multi-language publishers / labels
 *     3. Shared South Indian / Dravidian words across languages (credits all member languages)
 *     4. English loanwords / function words
 * - Threshold: score >= 2 identifies language (1 strong or >=2 distinct weak).
 * - A tie at max score between 2+ languages cannot decide -> UNKNOWN.
 * - Latin script with no evidence is UNKNOWN (never English by default).
 * - English requires positive evidence: known English artist OR >=3 English words without competing signals.
 */
object LanguageStarterData {

    // Verified single-language exclusive channels/labels (STRONG = 2 points)
    val EXCLUSIVE_LABELS: Map<Language, List<String>> = mapOf(
        Language.MALAYALAM to listOf(
            "saregama malayalam",
            "speed audio malayalam",
            "satyam audios",
            "satyam jukebox",
            "manorama music",
            "millennium audios",
            "saina music",
            "mathrubhumi",
            "east coast",
            "goodwill entertainments",
        ),
        Language.TAMIL to listOf(
            "saregama tamil",
            "lahari music tamil",
            "sun pictures",
            "u1 records",
        ),
        Language.TELUGU to listOf(
            "saregama telugu",
            "t-series telugu",
            "madhura audio",
        ),
        Language.HINDI to listOf(
            "saregama hindi",
        ),
    )

    // Multi-language publishers / labels (WEAK = 1 point, credited to all languages they publish)
    val WEAK_LABELS: Map<Language, List<String>> = mapOf(
        Language.MALAYALAM to listOf(
            "muzik247",
            "think music india",
            "sony music south",
            "lahari music",
        ),
        Language.TAMIL to listOf(
            "think music india",
            "sony music south",
            "lahari music",
            "lyca productions",
            "muzik247",
        ),
        Language.TELUGU to listOf(
            "aditya music",
            "lahari music",
            "sony music south",
            "think music india",
        ),
        Language.HINDI to listOf(
            "t-series",
            "zee music company",
            "tips official",
            "sony music india",
            "yrf",
        ),
    )

    // Language-specific unique words (STRONG evidence = 2 points)
    val STRONG_VOCABULARY: Map<Language, List<String>> = mapOf(
        Language.MALAYALAM to listOf(
            "njan", "njanum", "ningal", "ithu", "ivide", "entethu",
            "ippol", "eppol", "enthaanu", "pakshe", "engane",
            "pennu", "cheru", "cheriya", "paattu", "gaanangal",
            "ormakal", "hridayam", "jeevitham", "snehamodu", "chundathu",
        ),
        Language.TAMIL to listOf(
            "naan", "epdi", "inga", "unga", "avanga", "paatu",
            "kaadhal", "kanmani", "serndhu", "vaazhkai",
        ),
        Language.TELUGU to listOf(
            "nenu", "ela", "ekkada", "meeru", "prema", "paata",
            "cheliya", "manasu", "kaadhu", "vasthe",
        ),
        Language.HINDI to listOf(
            "hum", "aap", "zindagi", "kaha", "kyun",
            "hona", "dhadkan", "mohabbat", "khuda",
        ),
    )

    // Multilingual artists with alias groups (WEAK evidence = 1 point per artist entity).
    // Aliases of the same artist are grouped so an artist can contribute at most 1 point in total.
    val MULTILINGUAL_ARTISTS: Map<Language, List<List<String>>> = mapOf(
        Language.MALAYALAM to listOf(
            listOf("k.j. yesudas", "yesudas"),
            listOf("k.s. chithra", "chithra"),
            listOf("sujatha mohan"),
            listOf("m.g. sreekumar"),
            listOf("sushin shyam"),
            listOf("hesham abdul wahab"),
            listOf("jassie gift"),
            listOf("vineeth sreenivasan"),
            listOf("shaan rahman"),
            listOf("gopi sundar"),
            listOf("deepak dev"),
            listOf("vidyasagar"),
            listOf("raveendran"),
            listOf("ouseppachan"),
            listOf("bijibal"),
            listOf("m. jayachandran"),
            listOf("job kurian"),
            listOf("sachin warrier"),
            listOf("unni menon"),
            listOf("madhu balakrishnan"),
        ),
        Language.TAMIL to listOf(
            listOf("anirudh"),
            listOf("ilaiyaraaja"),
            listOf("a.r. rahman"),
            listOf("sid sriram"),
            listOf("dhanush"),
            listOf("yuvan shankar raja"),
            listOf("harris jayaraj"),
            listOf("s.p. balasubrahmanyam", "spb"),
        ),
        Language.TELUGU to listOf(
            listOf("devi sri prasad", "dsp"),
            listOf("thaman s", "thaman"),
            listOf("sid sriram"),
            listOf("anurag kulkarni"),
            listOf("ram miriyala"),
            listOf("s.p. balasubrahmanyam", "spb"),
        ),
        Language.HINDI to listOf(
            listOf("arijit singh"),
            listOf("neha kakkar"),
            listOf("badshah"),
            listOf("shreya ghoshal"),
            listOf("atif aslam"),
            listOf("kk"),
            listOf("sonu nigam"),
            listOf("pritam"),
            listOf("vishal mishra"),
        ),
    )

    // Shared words across languages (WEAK evidence = 1 point credited to EVERY listed language).
    // Because each language gets 1 point, shared words cannot decide between those languages.
    val SHARED_WORDS: Map<String, List<Language>> = mapOf(
        "oru" to listOf(Language.MALAYALAM, Language.TAMIL),
        "neram" to listOf(Language.MALAYALAM, Language.TAMIL),
        "neramu" to listOf(Language.TELUGU),
        "uyire" to listOf(Language.MALAYALAM, Language.TAMIL, Language.TELUGU),
        "kannu" to listOf(Language.MALAYALAM, Language.TAMIL, Language.TELUGU),
        "mazha" to listOf(Language.MALAYALAM, Language.TAMIL),
        "mazhai" to listOf(Language.TAMIL),
        "kalyanam" to listOf(Language.MALAYALAM, Language.TAMIL, Language.TELUGU),
        "premam" to listOf(Language.MALAYALAM, Language.TAMIL),
        "prema" to listOf(Language.TELUGU),
        "kanmani" to listOf(Language.MALAYALAM, Language.TAMIL),
        "theeram" to listOf(Language.MALAYALAM, Language.TAMIL),
        "puzha" to listOf(Language.MALAYALAM, Language.TAMIL),
        "kaathu" to listOf(Language.MALAYALAM, Language.TAMIL),
        "vinnile" to listOf(Language.MALAYALAM, Language.TAMIL),
        "enna" to listOf(Language.MALAYALAM, Language.TAMIL),
        "varum" to listOf(Language.MALAYALAM, Language.TAMIL),
        "thumbi" to listOf(Language.MALAYALAM, Language.TAMIL),
        "kaattil" to listOf(Language.MALAYALAM, Language.TAMIL),
        "thaamara" to listOf(Language.MALAYALAM, Language.TAMIL),
        "moham" to listOf(Language.MALAYALAM, Language.TAMIL),
        "priyasakhi" to listOf(Language.MALAYALAM, Language.TAMIL),
        "shantham" to listOf(Language.MALAYALAM, Language.TAMIL, Language.TELUGU),
        "dil" to listOf(Language.HINDI),
        "pyaar" to listOf(Language.HINDI),
        "ishq" to listOf(Language.HINDI),
        "tum" to listOf(Language.HINDI),
        "tera" to listOf(Language.HINDI),
        "mera" to listOf(Language.HINDI),
        "meri" to listOf(Language.HINDI),
        "tere" to listOf(Language.HINDI),
    )

    // Backward compatibility accessor for SHARED_VOCABULARY
    val SHARED_VOCABULARY: Map<Language, List<String>> by lazy {
        val result = mutableMapOf<Language, MutableList<String>>()
        SHARED_WORDS.forEach { (word, langs) ->
            langs.forEach { lang ->
                result.getOrPut(lang) { mutableListOf() }.add(word)
            }
        }
        result
    }

    // Function words and common Indian title loanwords (WEAK English = 1 point)
    val ENGLISH_WEAK_WORDS: List<String> = listOf(
        "the", "and", "you", "your", "with", "for", "from", "of", "to", "in",
        "me", "my", "we", "our", "he", "she", "it", "they", "this", "that",
        "is", "are", "was", "be", "have", "do", "so", "not", "no",
        "love", "heart", "night", "dance", "dream", "life", "baby", "girl", "boy", "feel",
    )

    // Distinct English positive vocabulary
    val ENGLISH_POSITIVE_WORDS: List<String> = listOf(
        "world", "time", "like", "never", "always", "stay", "eyes", "say", "know", "see",
        "think", "forever", "together", "light", "dark", "broken", "fire", "rain",
        "tonight", "need", "hold", "away", "without", "someone", "somewhere",
        "closer", "myself", "yourself", "beautiful", "memories", "perfect", "shape",
        "new",
    )

    // Recognized Western / English artists
    val ENGLISH_ARTISTS: List<String> = listOf(
        "ed sheeran", "the weeknd", "taylor swift", "drake", "adele",
        "coldplay", "billie eilish", "bruno mars", "justin bieber",
        "dua lipa", "eminem", "rihanna", "post malone", "shawn mendes",
        "ellie goulding", "maroon 5", "imagine dragons", "charlie puth",
        "ariana grande", "sam smith", "olivia rodrigo", "harry styles",
        "katy perry", "lady gaga", "sia", "alan walker",
    )
}
