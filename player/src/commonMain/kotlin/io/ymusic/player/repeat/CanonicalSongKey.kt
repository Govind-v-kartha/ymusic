package io.ymusic.player.repeat

object CanonicalSongKey {
    private val BRACKETED_PATTERNS = Regex("""(?i)\s*[\[(](?:official\s*(?:video|audio|music\s*video|lyric\s*video|visualizer|hd|4k|mv)?|audio|lyric\s*video|lyrics|music\s*video|visualizer|remaster(?:ed)?(?:\s*\d+)?|live|prod\.?[^])]*|feat\.?[^])]*|ft\.?[^])]*|hd|4k|mv|hq)[\])]""")
    private val FEAT_SUFFIX_PATTERNS = Regex("""(?i)\s*(?:feat\.?|ft\.?)\s+.*$""")
    private val PUNCTUATION_PATTERNS = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE_PATTERNS = Regex("""\s+""")

    fun compute(artist: String?, title: String?, fallbackKey: String): String {
        val rawTitle = title?.trim().orEmpty()
        if (rawTitle.isEmpty()) return fallbackKey.trim().lowercase()

        var parsedArtist = artist?.trim().orEmpty()
        var parsedTitle = rawTitle

        // Handle YouTube title formats like "Artist - Title"
        if (parsedTitle.contains(" - ")) {
            val parts = parsedTitle.split(" - ", limit = 2)
            if (parts.size == 2) {
                val titleArtist = parts[0].trim()
                val songTitle = parts[1].trim()
                if (parsedArtist.isEmpty() || parsedArtist.equals(titleArtist, ignoreCase = true)) {
                    parsedArtist = titleArtist
                    parsedTitle = songTitle
                }
            }
        }

        val normTitle = normalizeText(parsedTitle)
        val normArtist = normalizeText(parsedArtist)

        if (normTitle.isEmpty()) return fallbackKey.trim().lowercase()
        return if (normArtist.isNotEmpty()) "$normArtist|$normTitle" else normTitle
    }

    private fun normalizeText(text: String): String {
        var s = text
        // Strip bracketed descriptors like (Official Video), [Audio], (Remastered 2011), etc.
        s = BRACKETED_PATTERNS.replace(s, "")
        // Strip trailing feat./ft. clauses
        s = FEAT_SUFFIX_PATTERNS.replace(s, "")
        // Strip punctuation
        s = PUNCTUATION_PATTERNS.replace(s, " ")
        // Collapse spaces and lowercase
        return WHITESPACE_PATTERNS.replace(s, " ").trim().lowercase()
    }
}
