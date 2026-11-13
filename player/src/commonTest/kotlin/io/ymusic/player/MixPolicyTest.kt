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
        // Strong vocabulary unique to Malayalam
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Oru Cheru Kadhakal", "Independent Artist"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Njanum Ente Jeevitham", "Singer"))

        // Two weak signals (multilingual artist + shared vocabulary)
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Mazha Thorum Neram", "K.J. Yesudas"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Ente Sneham Priyasakhi", "K.S. Chithra"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Chundathu Chempattu", "Hesham Abdul Wahab"))

        // Single weak word with no artist/context produces UNKNOWN
        assertEquals(Language.UNKNOWN, LanguageClassifier.classify("Kalyanam", "Unknown Artist"))

        // Latin script with no evidence produces UNKNOWN (never English by default)
        assertEquals(Language.UNKNOWN, LanguageClassifier.classify("Xylo Zaphyr", "Quasar"))
    }

    @Test
    fun testMalayalamChannelAndArtistHints() {
        // Strong label/channel hint
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Romantic Melody Song", "Muzik247"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Evergreen Hit", "Satyam Jukebox"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("New Movie Track", "Manorama Music"))
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Classic Track", "Millennium Audios"))

        // Multi-signal artist + shared word
        assertEquals(Language.MALAYALAM, LanguageClassifier.classify("Thumbi Vaa", "K.S. Chithra"))
    }

    @Test
    fun testPreferredLanguageNeverOverridesSeed() {
        val policy = MixPolicyImpl(
            preferredLanguage = { "ml" }, // User prefers Malayalam
        )

        // Seed with Hindi song (strong vocab + artist)
        val seed = fakeItem("hindi_seed", "Tum Hi Ho Dil Ki Dhadkan", "Arijit Singh")
        policy.onManualPlay(seed)

        // Target language must be HINDI, NOT Malayalam!
        assertEquals(Language.HINDI, policy.context.getTargetLanguage())
    }

    @Test
    fun testPreferredLanguageUsedWhenSeedIsUnknown() {
        val policy = MixPolicyImpl(
            preferredLanguage = { "ml" },
        )

        // Seed with completely unknown Latin title
        val seed = fakeItem("unknown_seed", "Xylo Zaphyr", "Quasar")
        policy.onManualPlay(seed)

        // Target language falls back to preferredLanguage (Malayalam)
        assertEquals(Language.MALAYALAM, policy.context.getTargetLanguage())
    }

    @Test
    fun testMelodyVsEnergeticClassificationAndSelection() {
        val policy = MixPolicyImpl(
            songMixEnabled = { true },
            keepMoodEnabled = { true },
            preferredLanguage = { "ml" },
        )

        // Seed with Malayalam melody (using strong label or vocab)
        val seed = fakeItem("seed", "Oru Cheru Gaanam (Soft Acoustic Melody Love Song)", "Muzik247")
        policy.onManualPlay(seed)
        assertEquals(Language.MALAYALAM, policy.context.getTargetLanguage())
        assertEquals(Mood.CALM, policy.context.getTargetMood())

        val items = listOf(
            fakeItem("0", "DJ Fast Party Club Dance Remix Beat", "Remix King"), // Energetic
            fakeItem("1", "Oru Hridayam (Soft Acoustic Slow Piano)", "Muzik247"), // Calm melody Malayalam
            fakeItem("2", "Heavy Bass Trap EDM Workout", "Beat Master"), // Energetic
        )

        val result = policy.selectNextCandidate(listOf(0, 1, 2), items, currentIndex = 0)
        // Item 1 is the calm melody matching target language + mood
        assertEquals(1, result.selectedIndex)
    }

    data class RadioSample(
        val expectedLanguage: Language,
        val title: String,
        val artist: String,
        val channel: String,
    )

    @Test
    fun testRadioClassifierEvidenceOn60RealisticTitles() {
        val samples = listOf(
            // 15 Malayalam realistic radio titles
            RadioSample(Language.MALAYALAM, "Aalorungi Arangum Oru Paattu (Official Video)", "K.J. Yesudas", "Satyam Audios"),
            RadioSample(Language.MALAYALAM, "Njanum Njanumentaalum (Lyric Video)", "Sushin Shyam", "Saina Music"),
            RadioSample(Language.MALAYALAM, "Uyire Uyire Ente Jeevitham (Official Music Video)", "Sujatha Mohan", "Muzik247"),
            RadioSample(Language.MALAYALAM, "Entethu Mathram Pennu (Official Video)", "Shaan Rahman", "Manorama Music"),
            RadioSample(Language.MALAYALAM, "Mazha Thorum Neram (Official Video)", "K.J. Yesudas", "East Coast"),
            RadioSample(Language.MALAYALAM, "Pookkalam Njan Oru (Lyric Video)", "K.S. Chithra", "Millennium Audios"),
            RadioSample(Language.MALAYALAM, "Hridayam Ennum Ormakal (Official Video)", "Hesham Abdul Wahab", "Goodwill Entertainments"),
            RadioSample(Language.MALAYALAM, "Snehamodu Ningal Ivide (Lyric Video)", "Vineeth Sreenivasan", "Mathrubhumi"),
            RadioSample(Language.MALAYALAM, "Cheriya Cheru Gaanangal (Official Video)", "Job Kurian", "Saregama Malayalam"),
            RadioSample(Language.MALAYALAM, "Ippol Eppol Enthaanu (Official Video)", "Deepak Dev", "Speed Audio Malayalam"),
            RadioSample(Language.MALAYALAM, "Puzha Theeram Vinnile (Lyric Video)", "M. Jayachandran", "East Coast"),
            RadioSample(Language.MALAYALAM, "Kalyanam Kazhicha Pennu (Official Video)", "Gopi Sundar", "Muzik247"),
            RadioSample(Language.MALAYALAM, "Oru Paattu Paadam (Lyric Video)", "Unni Menon", "Satyam Jukebox"),
            RadioSample(Language.MALAYALAM, "Moham Enna Jeevitham (Official Video)", "Madhu Balakrishnan", "Manorama Music"),
            RadioSample(Language.MALAYALAM, "Neram Poyi Chundathu (Lyric Video)", "Bijibal", "Saina Music"),

            // 15 Hindi realistic radio titles
            RadioSample(Language.HINDI, "Tum Hi Ho (Official Video)", "Arijit Singh", "T-Series"),
            RadioSample(Language.HINDI, "Dil Dhadkan Aur Pyaar (Lyric Video)", "Sonu Nigam", "Sony Music India"),
            RadioSample(Language.HINDI, "Zindagi Tere Naam (Official Video)", "Shreya Ghoshal", "Zee Music Company"),
            RadioSample(Language.HINDI, "Meri Mohabbat Mera Khuda (Lyric Video)", "Atif Aslam", "Tips Official"),
            RadioSample(Language.HINDI, "Tera Ishq Bada Teekha (Official Video)", "Badshah", "T-Series"),
            RadioSample(Language.HINDI, "Hum Tum Ek Kamre Mein (Lyric Video)", "KK", "YRF"),
            RadioSample(Language.HINDI, "Aap Jaisa Koi Meri (Official Video)", "Neha Kakkar", "T-Series"),
            RadioSample(Language.HINDI, "Kyun Kaha Tha Tune (Lyric Video)", "Pritam", "Sony Music India"),
            RadioSample(Language.HINDI, "Hona Tha Pyaar (Official Video)", "Atif Aslam", "Tips Official"),
            RadioSample(Language.HINDI, "Mera Dil Meri Jaan (Lyric Video)", "Vishal Mishra", "Zee Music Company"),
            RadioSample(Language.HINDI, "Tere Bina Zindagi (Official Video)", "Arijit Singh", "T-Series"),
            RadioSample(Language.HINDI, "Pyaar Kiya Toh Nibhana (Lyric Video)", "Sonu Nigam", "Tips Official"),
            RadioSample(Language.HINDI, "Dil Ki Dhadkan Sun (Official Video)", "Shreya Ghoshal", "YRF"),
            RadioSample(Language.HINDI, "Ishq Hai Mera (Lyric Video)", "KK", "Sony Music India"),
            RadioSample(Language.HINDI, "Khuda Aur Mohabbat (Official Video)", "Arijit Singh", "T-Series"),

            // 15 Tamil realistic radio titles
            RadioSample(Language.TAMIL, "Kaadhal Kanmani Enna Panna (Official Video)", "Anirudh", "Sony Music South"),
            RadioSample(Language.TAMIL, "Naan Epdi Solvaen (Lyric Video)", "Sid Sriram", "Think Music India"),
            RadioSample(Language.TAMIL, "Inga Unga Vaazhkai (Official Video)", "Yuvan Shankar Raja", "U1 Records"),
            RadioSample(Language.TAMIL, "Oru Paatu Serndhu Paadu (Lyric Video)", "Ilaiyaraaja", "Saregama Tamil"),
            RadioSample(Language.TAMIL, "Avanga Varum Neram (Official Video)", "Harris Jayaraj", "Sun Pictures"),
            RadioSample(Language.TAMIL, "Uyire Uyire Serndhu (Lyric Video)", "A.R. Rahman", "Sony Music South"),
            RadioSample(Language.TAMIL, "Kalyanam Panna Aasai (Official Video)", "Dhanush", "Lyca Productions"),
            RadioSample(Language.TAMIL, "Thenral Kaathu Veesum (Lyric Video)", "S.P. Balasubrahmanyam", "Saregama Tamil"),
            RadioSample(Language.TAMIL, "Enna Solla Pogirai (Official Video)", "Harris Jayaraj", "Think Music India"),
            RadioSample(Language.TAMIL, "Mazhai Varum Neram (Lyric Video)", "Sid Sriram", "Lahari Music Tamil"),
            RadioSample(Language.TAMIL, "Kaadhal Oru Aagayam (Official Video)", "Anirudh", "Sony Music South"),
            RadioSample(Language.TAMIL, "Naan Unnai Paartha (Lyric Video)", "Yuvan Shankar Raja", "U1 Records"),
            RadioSample(Language.TAMIL, "Vaazhkai Oru Vaanam (Official Video)", "Ilaiyaraaja", "Saregama Tamil"),
            RadioSample(Language.TAMIL, "Kannu Kulla Nikkira (Lyric Video)", "Dhanush", "Sun Pictures"),
            RadioSample(Language.TAMIL, "Priya Sakhi Kaadhal (Official Video)", "A.R. Rahman", "Think Music India"),

            // 15 English realistic radio titles
            RadioSample(Language.ENGLISH, "Shape of You (Official Video)", "Ed Sheeran", "Ed Sheeran"),
            RadioSample(Language.ENGLISH, "Blinding Lights (Official Music Video)", "The Weeknd", "TheWeekndVEVO"),
            RadioSample(Language.ENGLISH, "Love Story (Taylor's Version) (Lyric Video)", "Taylor Swift", "TaylorSwiftVEVO"),
            RadioSample(Language.ENGLISH, "Hold On (Official Video)", "Justin Bieber", "JustinBieberVEVO"),
            RadioSample(Language.ENGLISH, "Someone Like You (Official Video)", "Adele", "AdeleVEVO"),
            RadioSample(Language.ENGLISH, "Bad Guy (Official Music Video)", "Billie Eilish", "BillieEilishVEVO"),
            RadioSample(Language.ENGLISH, "Fire on Fire (Official Video)", "Sam Smith", "SamSmithVEVO"),
            RadioSample(Language.ENGLISH, "Stay With Me (Lyric Video)", "Sam Smith", "SamSmithVEVO"),
            RadioSample(Language.ENGLISH, "Memories (Official Video)", "Maroon 5", "Maroon5VEVO"),
            RadioSample(Language.ENGLISH, "Perfect (Official Music Video)", "Ed Sheeran", "Ed Sheeran"),
            RadioSample(Language.ENGLISH, "Dance The Night (From Barbie The Album) (Official Video)", "Dua Lipa", "Dua Lipa"),
            RadioSample(Language.ENGLISH, "Believer (Official Music Video)", "Imagine Dragons", "ImagineDragonsVEVO"),
            RadioSample(Language.ENGLISH, "Closer (Lyric Video)", "The Chainsmokers", "ChainsmokersVEVO"),
            RadioSample(Language.ENGLISH, "Beautiful Things (Official Video)", "Benson Boone", "Benson Boone"),
            RadioSample(Language.ENGLISH, "Rain On Me (Official Music Video)", "Lady Gaga", "LadyGagaVEVO"),
        )

        var correctCount = 0
        var wrongCount = 0
        var unknownCount = 0
        var withMoodCount = 0

        println("=== 60 REALISTIC RADIO TITLES CLASSIFICATION REPORT ===")
        println("%-3s | %-9s | %-9s | %-9s | %-45s | %-20s | %s".format(
            "#", "Expected", "Detected", "Mood", "Title", "Artist / Channel", "Signals Used (Strength)"
        ))
        println("-".repeat(140))

        samples.forEachIndexed { index, sample ->
            val evidence = LanguageClassifier.classifyWithEvidence(
                title = sample.title,
                artist = sample.artist,
                channel = sample.channel,
            )
            val detectedMood = io.ymusic.player.mix.MoodClassifier.classify(sample.title, sample.artist)

            val isCorrect = evidence.language == sample.expectedLanguage
            val isUnknown = evidence.language == Language.UNKNOWN
            if (isCorrect) {
                correctCount++
            } else if (isUnknown) {
                unknownCount++
            } else {
                wrongCount++
            }

            if (detectedMood != Mood.UNKNOWN) {
                withMoodCount++
            }

            val signalsFormatted = if (evidence.signals.isEmpty()) {
                "None"
            } else {
                evidence.signals.joinToString("; ") { "${it.first} [${it.second}pt]" }
            }

            println("%-3d | %-9s | %-9s | %-9s | %-45s | %-20s | %s".format(
                index + 1,
                sample.expectedLanguage.name,
                evidence.language.name,
                detectedMood.name,
                sample.title.take(45),
                "${sample.artist} / ${sample.channel}".take(20),
                signalsFormatted,
            ))
        }

        val total = samples.size
        val moodSharePercent = (withMoodCount * 100.0) / total
        println("-".repeat(140))
        println("TOTALS: Total=$total | Correct=$correctCount | Wrong=$wrongCount | Unknown=$unknownCount | Mood Evidence=$withMoodCount (${moodSharePercent.toInt()}%)")
        println("=========================================================")

        assertEquals(0, wrongCount, "Expected zero wrong classifications across 60 realistic radio titles")
        assertTrue(correctCount >= 55, "Expected high accuracy (>=55/60) with starter dictionary")
    }
}
