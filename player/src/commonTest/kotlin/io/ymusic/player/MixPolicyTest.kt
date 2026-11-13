package io.ymusic.player

import io.ymusic.player.mix.Language
import io.ymusic.player.mix.LanguageClassifier
import io.ymusic.player.mix.LearnedArtistLanguageStore
import io.ymusic.player.mix.MixPolicyImpl
import io.ymusic.player.mix.Mood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MixPolicyTest {

    private fun fakeItem(id: String, title: String, artist: String): PlayableItem {
        return PlayableItem.YtMedia(
            videoId = id,
            title = title,
            artist = artist,
        )
    }

    @Test
    fun testScriptClassifierOnRealTitles() {
        // Malayalam real titles
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("മനസ്സിലായോ", "Anirudh"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("കണ്ണേ കണ്ണേ", "K.S. Chithra"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("ആളൊരുങ്ങി അരങ്ങൊരുങ്ങി", "Yesudas"))

        // Tamil real titles
        assertEquals(Language.TAMIL, LanguageClassifier.classify("கண்ணே கலைமானே", "Ilaiyaraaja"))
        assertEquals(Language.TAMIL, LanguageClassifier.classify("வாத்தி கமிங்", "Anirudh"))

        // Telugu real titles
        assertEquals(Language.TELUGU, LanguageClassifier.classify("సామజవరగమన", "Sid Sriram"))
        assertEquals(Language.TELUGU, LanguageClassifier.classify("బుట్ట బొమ్మ", "Armaan Malik"))

        // Kannada real titles
        assertEquals(Language.KANNADA, LanguageClassifier.classify("ಬೆಳಗೆದ್ದು ಯಾರ ಮುಖವ ನಾನು ನೋಡಿದೆ", "Vijay Prakash"))

        // Hindi real titles
        assertEquals(Language.HINDI, LanguageClassifier.classify("तुम ही हो", "Arijit Singh"))
        assertEquals(Language.HINDI, LanguageClassifier.classify("केसरिया तेरा इश्क है पिया", "Arijit Singh"))

        // Arabic real titles
        assertEquals(Language.ARABIC, LanguageClassifier.classify("حبيبي يا نور العين", "Amr Diab"))

        // English real titles
        assertEquals(Language.ENGLISH, LanguageClassifier.classify("Shape of You", "Ed Sheeran"))
        assertEquals(Language.ENGLISH, LanguageClassifier.classify("Blinding Lights", "The Weeknd"))

        // Keyword tags in Latin script
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Manasilaayo [Malayalam Song]", "Various"))
        assertEquals(Language.TAMIL, LanguageClassifier.classify("Rowdy Baby (Tamil Video)", "Dhanush"))
        assertEquals(Language.HINDI, LanguageClassifier.classify("Chaleya (Hindi Version)", "Anirudh"))
    }

    @Test
    fun testLearnedChannelDictionaryCountAndPercentageGates() {
        val store = LearnedArtistLanguageStore()
        val artist = "Malayalam Music Channel"

        // 1 observation: total 1 (<3), not decisive
        store.recordObservation(artist, Language.MALAYALAM)
        assertNull(store.getDecisiveLanguage(artist))

        // 2 observations: total 2 (<3), not decisive
        store.recordObservation(artist, Language.MALAYALAM)
        assertNull(store.getDecisiveLanguage(artist))

        // 3 observations: total 3 (>=3), 3/3 = 100% (>=70%), decisive!
        store.recordObservation(artist, Language.MALAYALAM)
        assertEquals(Language.MALAYALAM, store.getDecisiveLanguage(artist))

        // Transliterated title with no script or keywords should now resolve via learned artist
        val classified = LanguageClassifier.classify("Njanum Njanumentaalum", artist, store)
        assertEquals(Language.MALAYALAM, classified)
    }

    @Test
    fun testMixedChannelNonDecisiveness() {
        val store = LearnedArtistLanguageStore()
        val channel = "Mega Diverse Music Label"

        // Channel posts songs in Hindi, Tamil, and Telugu evenly
        store.recordObservation(channel, Language.HINDI)
        store.recordObservation(channel, Language.HINDI)
        store.recordObservation(channel, Language.TAMIL)
        store.recordObservation(channel, Language.TAMIL)
        store.recordObservation(channel, Language.TELUGU)
        store.recordObservation(channel, Language.TELUGU)

        // Total 6 observations (>=3), but top language is 2/6 = 33.3% (< 70%)
        assertNull(store.getDecisiveLanguage(channel))

        // LanguageClassifier should NOT decide language based on this non-decisive channel
        val lang = LanguageClassifier.classify("New Instrumental Track", channel, store)
        assertEquals(Language.ENGLISH, lang) // falls back to English/Latin text instead of false Indic tag
    }

    @Test
    fun testRatioWindowDeficitTrackingAcross100Picks() {
        val policy = MixPolicyImpl()

        // User seeds with Malayalam manual play
        val seed = fakeItem("seed1", "മനസ്സിലായോ", "Artist ML")
        policy.onManualPlay(seed)
        assertEquals(Language.MALAYALAM, policy.context.getTargetLanguage())

        // Candidates: 5 Malayalam songs, 5 English songs
        val items = mutableListOf<PlayableItem>()
        for (i in 0 until 5) {
            items.add(fakeItem("ml_$i", "മലയാളം പാട്ട് $i", "Malayalam Artist"))
        }
        for (i in 0 until 5) {
            items.add(fakeItem("en_$i", "English Pop Song $i", "English Artist"))
        }
        val candidates = items.indices.toList()

        var targetCount = 0
        var discoveryCount = 0

        // Simulate 100 picks
        for (step in 0 until 100) {
            val result = policy.selectNextCandidate(candidates, items, currentIndex = 0)
            val pickedIdx = result.selectedIndex ?: error("Failed to select")
            val pickedItem = items[pickedIdx]

            policy.onAutoPick(pickedItem)

            val lang = LanguageClassifier.classify(pickedItem.displayTitle, pickedItem.artistName)
            if (lang == Language.MALAYALAM) {
                targetCount++
            } else {
                discoveryCount++
                // After a discovery pick, window must enforce deficit target priority
                assertTrue(policy.context.isTargetDeficit())
            }
        }

        // Over 100 picks with an 80/20 target ratio and deficit enforcement:
        // Target count should be approximately 80% (between 75 and 85)
        assertTrue(targetCount in 75..85, "Expected targetCount in 75..85, but was $targetCount")
        assertTrue(discoveryCount in 15..25, "Expected discoveryCount in 15..25, but was $discoveryCount")
    }

    @Test
    fun testMoodPreservation() {
        val policy = MixPolicyImpl()

        // Seed: Calm Malayalam track
        val seed = fakeItem("seed", "മനസ്സിലായോ (Acoustic Soft Piano)", "Singer")
        policy.onManualPlay(seed)
        assertEquals(Language.MALAYALAM, policy.context.getTargetLanguage())
        assertEquals(Mood.CALM, policy.context.getTargetMood())

        val items = listOf(
            fakeItem("0", "പാട്ട് ഒന്ന് (DJ Party Club Remix)", "Singer"), // Energetic
            fakeItem("1", "പാട്ട് രണ്ട് (Slow Acoustic Lofi Chill)", "Singer"), // Calm
            fakeItem("2", "പാട്ട് മൂന്ന് (Fast Trap Beat)", "Singer"), // Energetic
        )

        val result = policy.selectNextCandidate(listOf(0, 1, 2), items, currentIndex = 0)
        // Candidate 1 matches Target + Mood (CALM), so it must be selected
        assertEquals(1, result.selectedIndex)
    }

    @Test
    fun testQuickSkipPenalty() {
        val policy = MixPolicyImpl()

        val seed = fakeItem("seed", "മലയാളം പാട്ട് (Dance EDM Remix)", "Singer")
        policy.onManualPlay(seed) // Seeds Malayalam + Energetic

        // Candidate pool:
        // 0: Energetic Malayalam
        // 1: Calm Malayalam
        val items = listOf(
            fakeItem("0", "പാട്ട് ഒന്ന് (Remix Dance)", "Singer"),
            fakeItem("1", "പാട്ട് രണ്ട് (Acoustic Piano)", "Singer"),
        )

        // Before skip: Target+Mood picks energetic (item 0)
        val before = policy.selectNextCandidate(listOf(0, 1), items, 0)
        assertEquals(0, before.selectedIndex)

        // Quick skip item 0 at 3 seconds (<10s)
        policy.onQuickSkip(items[0], playDurationMs = 3000L)

        // After quick skip: Energetic Malayalam is penalized; Calm Malayalam (item 1) should be preferred!
        val after = policy.selectNextCandidate(listOf(0, 1), items, 0)
        assertEquals(1, after.selectedIndex)
    }

    @Test
    fun testPoolOfOne() {
        val policy = MixPolicyImpl()
        val items = listOf(fakeItem("single", "ഏകഗാനം", "ഗായകൻ"))
        val result = policy.selectNextCandidate(listOf(0), items, 0)
        assertEquals(0, result.selectedIndex)
        assertTrue(result.shouldRefill) // Pool has < 3 target candidates, should trigger refill
    }

    @Test
    fun testEmptyPoolFallback() {
        val policy = MixPolicyImpl()
        val result = policy.selectNextCandidate(emptyList(), emptyList(), -1)
        assertNull(result.selectedIndex)
        assertEquals(0, result.targetCandidateCount)
        assertFalse(result.shouldRefill)
    }

    @Test
    fun testRomanizedMalayalamClassifiedAsMalayalam() {
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Manikya Malaraya Poovi", "Vineeth Sreenivasan"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Neeyilla Neram", "Job Kurian"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Aaradhike", "Sushin Shyam"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Malare Ninne Kaanathirunnal", "Vijay Yesudas"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Vathikkalu Vellaripravu", "Arjun Ashokan"))
    }

    @Test
    fun testMalayalamChannelAndArtistHints() {
        // Label/Channel hints in artist or channel field
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Romantic Melody Song", "Muzik247"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Evergreen Hit", "Satyam Jukebox"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("New Movie Track", "Manorama Music"))

        // Prominent Malayalam artist names
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Chundathu Chempattu", "Hesham Abdul Wahab"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Thumbi Vaa", "K.S. Chithra"))
    }

    @Test
    fun testMelodyVsEnergeticClassificationAndSelection() {
        val policy = MixPolicyImpl(
            songMixEnabled = { true },
            keepMoodEnabled = { true },
        )

        // Seed with Malayalam melody
        val seed = fakeItem("seed", "Aaradhike (Melody Romantic Love Song)", "Sushin Shyam")
        policy.onManualPlay(seed)
        assertEquals(Language.MALAYALAM, policy.context.getTargetLanguage())
        assertEquals(Mood.CALM, policy.context.getTargetMood())

        val items = listOf(
            fakeItem("0", "DJ Fast Party Club Dance Remix Beat", "Remix King"), // Energetic
            fakeItem("1", "Malare (Soft Acoustic Slow Piano)", "Vijay Yesudas"), // Calm melody
            fakeItem("2", "Heavy Bass Trap EDM Workout", "Beat Master"), // Energetic
        )

        val result = policy.selectNextCandidate(listOf(0, 1, 2), items, currentIndex = 0)
        // Item 1 is the calm melody matching target language + mood
        assertEquals(1, result.selectedIndex)
    }
}
