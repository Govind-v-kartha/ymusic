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
 * - Weak signal (score = 1):
 *     1. Multilingual artists
 *     2. Shared South Indian / Dravidian words across languages
 * - Threshold: score >= 2 identifies language (1 strong or >=2 distinct weak).
 * - Latin script with no evidence is UNKNOWN (never English by default).
 * - English requires positive evidence (common English words or recognized English artists).
 */
object LanguageStarterData {

    val EXCLUSIVE_LABELS: Map<Language, List<String>> = mapOf(
        Language.MALAYALAM to listOf(
            "muzik247",
            "satyam audios",
            "satyam jukebox",
            "manorama music",
            "millennium audios",
            "saregama malayalam",
            "saina music",
            "speed audio malayalam",
            "mathrubhumi",
            "east coast",
            "goodwill entertainments",
        ),
        Language.TAMIL to listOf(
            "think music india",
            "sony music south",
            "saregama tamil",
            "sun pictures",
            "lyca productions",
            "lahari music tamil",
            "u1 records",
        ),
        Language.TELUGU to listOf(
            "aditya music",
            "madhura audio",
            "lahari music",
            "saregama telugu",
            "t-series telugu",
        ),
        Language.HINDI to listOf(
            "t-series",
            "zee music company",
            "yrf",
            "tips official",
            "sony music india",
        ),
    )

    // Language-specific unique words (STRONG evidence). Song-specific title pieces removed.
    val STRONG_VOCABULARY: Map<Language, List<String>> = mapOf(
        Language.MALAYALAM to listOf(
            "njan", "njanum", "ningal", "ithu", "ivide", "entethu",
            "ippol", "eppol", "enthaanu", "pakshe", "engane", "oru",
            "pennu", "cheru", "cheriya", "paattu", "gaanangal",
            "ormakal", "hridayam", "jeevitham", "snehamodu", "chundathu",
        ),
        Language.TAMIL to listOf(
            "naan", "enna", "epdi", "inga", "unga", "avanga", "paatu",
            "kaadhal", "kanmani", "varum", "serndhu", "vaazhkai",
        ),
        Language.TELUGU to listOf(
            "nenu", "ela", "ekkada", "meeru", "prema", "paata",
            "cheliya", "manasu", "kaadhu", "vasthe",
        ),
        Language.HINDI to listOf(
            "mera", "meri", "hum", "tum", "aap", "dil", "pyaar",
            "ishq", "zindagi", "tere", "tera", "kaha", "kyun",
            "hona", "dhadkan", "mohabbat", "khuda",
        ),
    )

    // Shared / Multilingual artists (WEAK evidence = 1 point)
    val MULTILINGUAL_ARTISTS: Map<Language, List<String>> = mapOf(
        Language.MALAYALAM to listOf(
            "k.j. yesudas", "yesudas", "k.s. chithra", "chithra", "sujatha mohan",
            "m.g. sreekumar", "sushin shyam", "hesham abdul wahab", "jassie gift",
            "vineeth sreenivasan", "shaan rahman", "gopi sundar", "deepak dev",
            "vidyasagar", "raveendran", "ouseppachan", "bijibal", "m. jayachandran",
            "job kurian", "sachin warrier", "unni menon", "madhu balakrishnan",
        ),
        Language.TAMIL to listOf(
            "anirudh", "ilaiyaraaja", "a.r. rahman", "sid sriram", "dhanush",
            "yuvan shankar raja", "harris jayaraj", "s.p. balasubrahmanyam", "spb",
        ),
        Language.TELUGU to listOf(
            "devi sri prasad", "dsp", "thaman s", "thaman", "sid sriram",
            "anurag kulkarni", "ram miriyala", "spb",
        ),
        Language.HINDI to listOf(
            "arijit singh", "neha kakkar", "badshah", "shreya ghoshal",
            "atif aslam", "kk", "sonu nigam", "pritam", "vishal mishra",
        ),
    )

    // Shared vocabulary across South Indian languages (WEAK evidence = 1 point)
    val SHARED_VOCABULARY: Map<Language, List<String>> = mapOf(
        Language.MALAYALAM to listOf(
            "kalyanam", "neram", "uyire", "kannu", "mazha", "premam",
            "moham", "kanmani", "priyasakhi", "shantham", "vinnile",
            "theeram", "puzha", "kaathu", "thumbi", "kaattil", "thaamara",
        ),
        Language.TAMIL to listOf(
            "kalyanam", "neram", "uyire", "kannu", "mazhai", "premam",
            "kanmani", "theeram", "kaathu", "thenral",
        ),
        Language.TELUGU to listOf(
            "kalyanam", "neramu", "uyire", "kannu", "prema", "sneham",
        ),
    )

    // Positive words required for English classification (excludes YouTube metadata boilerplate like official, video, lyrics, remix)
    val ENGLISH_POSITIVE_WORDS: List<String> = listOf(
        "the", "and", "you", "your", "with", "for", "from", "love", "night",
        "heart", "baby", "girl", "boy", "world", "time", "life", "like", "me",
        "never", "always", "stay", "eyes", "dream", "feel", "say", "know", "see",
        "think", "forever", "together", "light", "dark", "broken", "fire", "rain",
        "dance", "tonight", "need", "hold", "away", "without", "someone", "somewhere",
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
