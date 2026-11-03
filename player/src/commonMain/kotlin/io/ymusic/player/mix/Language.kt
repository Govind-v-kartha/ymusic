package io.ymusic.player.mix

enum class Language(val code: String, val label: String) {
    MALAYALAM("ml", "Malayalam"),
    TAMIL("ta", "Tamil"),
    TELUGU("te", "Telugu"),
    KANNADA("kn", "Kannada"),
    HINDI("hi", "Hindi"),
    ARABIC("ar", "Arabic"),
    ENGLISH("en", "English"),
    OTHER("other", "Other"),
    UNKNOWN("unknown", "Unknown");

    companion object {
        fun fromCode(code: String): Language =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
    }
}
