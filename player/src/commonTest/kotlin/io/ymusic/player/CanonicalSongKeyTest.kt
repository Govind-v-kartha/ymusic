package io.ymusic.player

import io.ymusic.player.repeat.CanonicalSongKey
import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalSongKeyTest {
    @Test
    fun normalizes_variants_of_same_song_to_identical_key() {
        val expected = "ed sheeran|shape of you"

        val k1 = CanonicalSongKey.compute("Ed Sheeran", "Shape of You", "vid1")
        val k2 = CanonicalSongKey.compute("Ed Sheeran", "Shape of You (Official Music Video)", "vid2")
        val k3 = CanonicalSongKey.compute("Ed Sheeran", "Shape of You [Official Audio]", "vid3")
        val k4 = CanonicalSongKey.compute(null, "Ed Sheeran - Shape of You (Lyric Video)", "vid4")
        val k5 = CanonicalSongKey.compute("Ed Sheeran", "Shape of You (Remastered 2021)", "vid5")
        val k6 = CanonicalSongKey.compute("Ed Sheeran", "Shape of You feat. Stormzy", "vid6")

        assertEquals(expected, k1)
        assertEquals(expected, k2)
        assertEquals(expected, k3)
        assertEquals(expected, k4)
        assertEquals(expected, k5)
        assertEquals(expected, k6)
    }

    @Test
    fun normalizes_regional_titles_with_english_letters() {
        val expected = "vineeth sreenivasan|manikya malaraya poovi"

        val k1 = CanonicalSongKey.compute("Vineeth Sreenivasan", "Manikya Malaraya Poovi", "v1")
        val k2 = CanonicalSongKey.compute("Vineeth Sreenivasan", "Manikya Malaraya Poovi (Official Video)", "v2")
        val k3 = CanonicalSongKey.compute(null, "Vineeth Sreenivasan - Manikya Malaraya Poovi [Audio]", "v3")

        assertEquals(expected, k1)
        assertEquals(expected, k2)
        assertEquals(expected, k3)
    }

    @Test
    fun fallbacks_to_fallback_key_when_title_is_blank() {
        assertEquals("fallback_id", CanonicalSongKey.compute("", "", "fallback_id"))
        assertEquals("fallback_id", CanonicalSongKey.compute(null, null, "FALLBACK_ID"))
    }
}
