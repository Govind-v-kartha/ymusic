package io.ymusic.player

import io.ymusic.player.mix.Language
import io.ymusic.player.mix.LanguageClassifier
import io.ymusic.player.mix.LearnedArtistLanguageStore
import io.ymusic.player.mix.MixContext
import io.ymusic.player.mix.MixPolicyImpl
import io.ymusic.player.mix.Mood
import io.ymusic.player.mix.MoodClassifier
import io.ymusic.player.repeat.CanonicalSongKey
import io.ymusic.player.repeat.RepeatEvent
import io.ymusic.player.repeat.RepeatPolicyImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerIntegrationTest {

    private open class FakeMediaEngine<S : PlayableItem> :
        MediaEngine<S>, VideoQualitySelectable, CaptionSelectable, CaptionRenderable, AudioTrackSelectable {
        val loads = mutableListOf<S>()
        val preloads = mutableListOf<S?>()
        private val _status = MutableStateFlow(PlaybackStatus(state = PlaybackState.Idle))
        override val status: StateFlow<PlaybackStatus> = _status.asStateFlow()
        private val _progress = MutableStateFlow(Progress())
        override val progress: StateFlow<Progress> = _progress.asStateFlow()

        override val videoQuality = MutableStateFlow(VideoQualityState.DEFAULT)
        override val captions = MutableStateFlow(CaptionState.DEFAULT)
        override val captionLines = MutableStateFlow<List<CaptionLine>>(emptyList())
        override val audioTracks = MutableStateFlow(AudioTrackState.DEFAULT)

        override fun selectAudioTrack(id: String) {}

        override fun preload(source: S?) {
            preloads.add(source)
        }

        override fun load(source: S, startPositionMs: Long, playWhenReady: Boolean, config: PlaybackConfig) {
            loads.add(source)
            _status.value = PlaybackStatus(
                state = PlaybackState.Prepared(playWhenReady, buffering = false, ended = false)
            )
            _progress.value = Progress(positionMs = startPositionMs, durationMs = 180_000)
        }

        fun finishTrack() {
            _status.update {
                it.copy(state = PlaybackState.Prepared(playWhenReady = false, buffering = false, ended = true))
            }
        }

        fun tickTo(positionMs: Long) {
            _progress.update { it.copy(positionMs = positionMs) }
        }

        override fun play() {
            _status.update {
                val state = it.state as? PlaybackState.Prepared ?: return@update it
                it.copy(state = state.copy(playWhenReady = true, ended = false))
            }
        }

        override fun pause() {
            _status.update {
                val state = it.state as? PlaybackState.Prepared ?: return@update it
                it.copy(state = state.copy(playWhenReady = false))
            }
        }

        override fun seekTo(positionMs: Long) {
            _progress.update { it.copy(positionMs = positionMs) }
        }

        override fun init() {
            if (_status.value.state is PlaybackState.Uninitialized) {
                _status.value = PlaybackStatus(state = PlaybackState.Idle)
            }
        }

        override fun reset() {
            _status.value = PlaybackStatus(state = PlaybackState.Idle)
            _progress.value = Progress()
        }

        override fun stop() {
            _status.value = PlaybackStatus(state = PlaybackState.Idle)
            _progress.value = Progress()
        }

        override fun setConfig(config: PlaybackConfig) {}
        override fun release() {}
    }

    private class FakeLocal : FakeMediaEngine<PlayableItem.LocalMedia>(), LocalPlayer
    private class FakeYt : FakeMediaEngine<PlayableItem.YtMedia>(), YtEngine {
        private val _reports = MutableSharedFlow<WatchNextSeen>(extraBufferCapacity = 4)
        override val watchNextSeen: SharedFlow<WatchNextSeen> = _reports
        override suspend fun requestPlayerResponse(videoId: String): String = ""
        override suspend fun requestPoToken(videoId: String): String = ""
    }

    private class FakeQueueSource(
        private val pages: List<List<PlayableItem>>,
    ) : QueueSource {
        var pageIndex = 0
        override val totalCount: Int? = null
        override suspend fun loadMore(): QueueSource.Page {
            if (pageIndex >= pages.size) return QueueSource.Page(emptyList(), hasMore = false)
            val p = pages[pageIndex++]
            return QueueSource.Page(p, hasMore = pageIndex < pages.size)
        }
    }

    private class Harness(
        scope: TestScope,
        repeatPolicy: RepeatPolicyImpl? = null,
        mixPolicy: MixPolicyImpl? = null,
        random: Random = Random(42),
    ) {
        val local = FakeLocal()
        val youtube = FakeYt()
        val player: Player = createPlayer(
            engines = PlayerEngines(
                local = { local },
                youtube = { youtube },
            ),
            mainDispatcher = StandardTestDispatcher(scope.testScheduler),
            random = random,
            repeatPolicy = repeatPolicy,
        ).apply {
            if (mixPolicy != null) {
                this.mixPolicy = mixPolicy
            }
        }
    }

    @Test
    fun radio_from_malayalam_melody_seed_mix_and_mood_protection() = runTest {
        val artistStore = LearnedArtistLanguageStore()
        val mixContext = MixContext()
        val mixPolicy = MixPolicyImpl(
            artistLanguageStore = artistStore,
            context = mixContext,
            songMixEnabled = { true },
            keepMoodEnabled = { true },
            preferredLanguage = { "ml" },
        )
        val repeatPolicy = RepeatPolicyImpl(Random(42))
        repeatPolicy.initSnapshot(emptyMap())

        val h = Harness(this, repeatPolicy = repeatPolicy, mixPolicy = mixPolicy)
        h.player.configureRepeatPolicy(noRepeatHours = { 48 })

        // 1. Seed: Malayalam Calm Melody
        val seed = PlayableItem.YtMedia("ml_seed", title = "Aaro Viral Neetti Lofi Melody", artist = "K.J. Yesudas")

        // 2. Pool: 12 pages of 5 items each (60 items total)
        // Each page contains 3 Malayalam calm, 1 Malayalam energetic, 1 discovery (Hindi/Tamil calm)
        val pages = (1..12).map { p ->
            listOf(
                PlayableItem.YtMedia("ml_calm_${p}_1", title = "Pookkalam Njanum Melody Song $p 1", artist = "K.J. Yesudas"),
                PlayableItem.YtMedia("ml_calm_${p}_2", title = "Aalorungi Ormakal Soulful Song $p 2", artist = "Chithra"),
                PlayableItem.YtMedia("ml_calm_${p}_3", title = "Mazha Hridayam Acoustic Calm $p 3", artist = "Sujatha Mohan"),
                PlayableItem.YtMedia("ml_energetic_$p", title = "Jimikki Kammal Remix EDM $p", artist = "Vineeth Sreenivasan"),
                if (p % 2 == 0) {
                    PlayableItem.YtMedia("hi_calm_$p", title = "Tum Hi Ho Acoustic $p", artist = "Arijit Singh")
                } else {
                    PlayableItem.YtMedia("ta_calm_$p", title = "Vaseegara Kaadhal Duet $p", artist = "Hariharan")
                }
            )
        }
        val queueSource = FakeQueueSource(pages)

        h.player.setQueue(listOf(seed), playIndex = 0, playWhenReady = true, source = queueSource)
        runCurrent()

        assertEquals(seed.videoId, (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        assertEquals(Language.MALAYALAM, mixContext.getTargetLanguage())
        assertEquals(Mood.CALM, mixContext.getTargetMood())

        // 3. 30 automatic advances through next-button and auto-advance paths
        val playedItems = mutableListOf<PlayableItem>()
        for (step in 1..30) {
            if (step % 2 == 1) {
                h.player.skipNext()
            } else {
                h.youtube.finishTrack()
            }
            runCurrent()
            val current = h.player.queue.value.currentItem
            assertNotNull(current, "Track should be loaded at step $step")
            playedItems.add(current)
        }

        assertEquals(30, playedItems.size)

        // 4. Assert every 10-pick window stays within one or two of target ratio (80% -> 6..10 target picks)
        // and NO energetic item is picked while calm candidates exist.
        for (start in 0..20) {
            val window = playedItems.subList(start, start + 10)
            val classifications = window.map { item ->
                val lang = LanguageClassifier.classify(item.displayTitle, item.artistName, artistStore, Language.MALAYALAM)
                val mood = MoodClassifier.classify(item.displayTitle, item.artistName)
                lang to mood
            }

            val targetPicks = classifications.count { it.first == Language.MALAYALAM }
            val energeticPicks = classifications.count { it.second == Mood.ENERGETIC }

            assertTrue(
                targetPicks in 6..10,
                "Window $start..${start + 10} expected 6..10 Malayalam picks, but got $targetPicks (stats: ${classifications.map { it.first }})"
            )
            assertEquals(
                0,
                energeticPicks,
                "Window $start..${start + 10} contained energetic picks while calm items existed"
            )
        }
    }

    @Test
    fun manual_play_of_hindi_song_moves_context_and_following_picks() = runTest {
        val artistStore = LearnedArtistLanguageStore()
        val mixContext = MixContext()
        val mixPolicy = MixPolicyImpl(
            artistLanguageStore = artistStore,
            context = mixContext,
            songMixEnabled = { true },
            keepMoodEnabled = { true },
            preferredLanguage = { "ml" },
        )
        val repeatPolicy = RepeatPolicyImpl(Random(42))
        repeatPolicy.initSnapshot(emptyMap())

        val h = Harness(this, repeatPolicy = repeatPolicy, mixPolicy = mixPolicy)
        h.player.configureRepeatPolicy(noRepeatHours = { 48 })

        val seed = PlayableItem.YtMedia("ml_seed", title = "Aaro Viral Neetti Lofi Melody", artist = "K.J. Yesudas")
        val hindiTrack = PlayableItem.YtMedia("hi_manual", title = "Tum Hi Ho Acoustic Melody", artist = "Arijit Singh")
        val pool = listOf(
            seed,
            hindiTrack,
            PlayableItem.YtMedia("hi_1", title = "Mera Dil Soulful Song", artist = "Arijit Singh"),
            PlayableItem.YtMedia("hi_2", title = "Tum Hi Aana Acoustic", artist = "Arijit Singh"),
            PlayableItem.YtMedia("hi_3", title = "Kabira Dil Ki Dhadkan", artist = "Arijit Singh"),
            PlayableItem.YtMedia("ml_other", title = "Aalorungi Song", artist = "Chithra"),
            PlayableItem.YtMedia("en_other", title = "Shape of You Acoustic", artist = "Ed Sheeran"),
        )

        val queueSource = FakeQueueSource(listOf(pool.drop(2)))
        h.player.setQueue(listOf(seed, hindiTrack), playIndex = 0, playWhenReady = true, source = queueSource)
        runCurrent()

        assertEquals(Language.MALAYALAM, mixContext.getTargetLanguage())

        // Manual play of Hindi song at index 1
        h.player.playAt(1)
        runCurrent()

        assertEquals(hindiTrack.videoId, (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        assertEquals(Language.HINDI, mixContext.getTargetLanguage(), "Manual play of Hindi song must move context to Hindi")

        // Following auto-picks should prioritize Hindi
        h.player.skipNext()
        runCurrent()
        val nextItem = h.player.queue.value.currentItem
        assertNotNull(nextItem)
        val nextLang = LanguageClassifier.classify(nextItem.displayTitle, nextItem.artistName, artistStore, Language.HINDI)
        assertEquals(Language.HINDI, nextLang, "Following pick must be Hindi after Hindi manual play")
    }

    @Test
    fun repeat_events_from_yesterday_block_same_song_and_alternative_upload() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val yesterday = now - 24 * 3600 * 1000L // 24 hours ago

        // Event for Shape of You played yesterday
        val soyOfficial = PlayableItem.YtMedia("soy_official", title = "Shape of You", artist = "Ed Sheeran")
        val soyAltUpload = PlayableItem.YtMedia("soy_alt", title = "Shape of You (Official Music Video)", artist = "Ed Sheeran")
        val believer = PlayableItem.YtMedia("believer_id", title = "Believer", artist = "Imagine Dragons")
        val radioactive = PlayableItem.YtMedia("radioactive_id", title = "Radioactive", artist = "Imagine Dragons")

        // Verify both uploads share the canonical key
        assertEquals(soyOfficial.canonicalKey, soyAltUpload.canonicalKey)

        // Simulate restart: new policy instance loaded from storage with yesterday's play event
        val repeatPolicy = RepeatPolicyImpl(Random(42))
        repeatPolicy.initSnapshot(
            mapOf(
                soyOfficial.repeatKey to listOf(
                    RepeatEvent(
                        playedAt = yesterday,
                        weight = 1.0,
                        canonicalKey = soyOfficial.canonicalKey,
                    )
                )
            )
        )

        val h = Harness(this, repeatPolicy = repeatPolicy)
        h.player.configureRepeatPolicy(noRepeatHours = { 48 }) // 48-hour no-repeat window

        // Queue contains believer, soyOfficial, soyAltUpload, radioactive
        // Believer plays first. Next should be radioactive, NOT either Shape of You upload.
        val queueSource = FakeQueueSource(emptyList())
        h.player.setQueue(listOf(believer, soyOfficial, soyAltUpload, radioactive), playIndex = 0, playWhenReady = true, source = queueSource)
        runCurrent()

        assertEquals("believer_id", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        h.player.skipNext()
        runCurrent()

        val current = (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId
        assertEquals("radioactive_id", current, "Both Shape of You uploads must be blocked by 48h window; radioactive flows")
    }

    @Test
    fun previous_history_across_skips_refill_playAt_insert_and_mutations() = runTest {
        val h = Harness(this)

        val a = PlayableItem.YtMedia("track_a", title = "Track A", artist = "Artist A")
        val b = PlayableItem.YtMedia("track_b", title = "Track B", artist = "Artist B")
        val c = PlayableItem.YtMedia("track_c", title = "Track C", artist = "Artist C")
        val d = PlayableItem.YtMedia("track_d", title = "Track D", artist = "Artist D")
        val e = PlayableItem.YtMedia("track_e", title = "Track E", artist = "Artist E")
        val refill1 = PlayableItem.YtMedia("track_refill_1", title = "Refill 1", artist = "Artist R")
        val refill2 = PlayableItem.YtMedia("track_refill_2", title = "Refill 2", artist = "Artist R")

        val queueSource = FakeQueueSource(listOf(listOf(refill1, refill2)))
        h.player.setQueue(listOf(a, b, c, d, e), playIndex = 0, playWhenReady = true, source = queueSource)
        runCurrent()

        // 1. Skips: A -> B -> C
        assertEquals("track_a", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        h.player.skipNext()
        runCurrent()
        assertEquals("track_b", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        h.player.skipNext()
        runCurrent()
        assertEquals("track_c", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // Previous returns B, then A
        h.player.skipPrev()
        runCurrent()
        assertEquals("track_b", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        h.player.skipPrev()
        runCurrent()
        assertEquals("track_a", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // 2. PlayAt jump: from A jump to E
        h.player.playAt(4) // index 4 is track_e
        runCurrent()
        assertEquals("track_e", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // Previous returns A
        h.player.skipPrev()
        runCurrent()
        assertEquals("track_a", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // 3. Play-next insertion: insert X right after current
        val x = PlayableItem.YtMedia("track_x", title = "Track X", artist = "Artist X")
        h.player.addToPlayNext(listOf(x))
        runCurrent()

        h.player.skipNext()
        runCurrent()
        assertEquals("track_x", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // Previous from X returns A
        h.player.skipPrev()
        runCurrent()
        assertEquals("track_a", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // Advance back to X, then advance past queue to trigger refill
        h.player.skipNext()
        runCurrent()
        assertEquals("track_x", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // 4. Queue mutations: reorder and remove items
        h.player.move(from = 3, to = 1) // move D
        runCurrent()
        h.player.removeAt(2) // remove an item
        runCurrent()

        // Previous from X still safely returns A
        h.player.skipPrev()
        runCurrent()
        assertEquals("track_a", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)

        // 5. Radio refill: advance through queue into refill items
        h.player.loadMore()
        runCurrent()
        assertTrue(h.player.queue.value.items.any { (it as? PlayableItem.YtMedia)?.videoId == "track_refill_1" })
    }

    @Test
    fun auto_picked_song_quick_skipped_is_rested_for_1_hour_manual_song_is_not() = runTest {
        val repeatPolicy = RepeatPolicyImpl(Random(42))
        repeatPolicy.initSnapshot(emptyMap())

        val h = Harness(this, repeatPolicy = repeatPolicy)
        h.player.configureRepeatPolicy(noRepeatHours = { 48 })

        val trackManual = PlayableItem.YtMedia("track_manual", title = "Manual Song", artist = "Artist A")
        val trackAuto = PlayableItem.YtMedia("track_auto", title = "Auto Picked Song", artist = "Artist B")
        val trackNext = PlayableItem.YtMedia("track_next", title = "Next Song", artist = "Artist C")

        var quickSkippedItem: PlayableItem? = null
        var quickSkippedWasAuto: Boolean? = null
        var autoQuickSkipCount = 0
        var manualQuickSkipCount = 0
        h.player.onTrackQuickSkipped = { item, isAuto ->
            quickSkippedItem = item
            quickSkippedWasAuto = isAuto
            if (isAuto) autoQuickSkipCount++ else manualQuickSkipCount++
        }

        // Set queue with trackManual (manual selection)
        h.player.setQueue(listOf(trackManual, trackAuto, trackNext), playIndex = 0, playWhenReady = true)
        runCurrent()

        assertEquals("track_manual", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        assertFalse(h.player.isCurrentItemAutoPicked)

        // 1. Quick skip on manual song (< 10s)
        h.youtube.tickTo(2000L) // 2s < 10s
        val now1 = Clock.System.now().toEpochMilliseconds()
        h.player.skipNext()
        runCurrent()

        assertEquals(trackManual, quickSkippedItem)
        assertEquals(false, quickSkippedWasAuto)
        assertEquals(1, manualQuickSkipCount)
        assertEquals(0, autoQuickSkipCount)
        assertFalse(repeatPolicy.isRested(trackManual.repeatKey, now1))

        // Current item is now trackAuto, which was reached via auto-advance / advance in queue
        assertEquals("track_auto", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        assertTrue(h.player.isCurrentItemAutoPicked)

        // 2. Quick skip on auto-picked song (< 10s)
        h.youtube.tickTo(3000L) // 3s < 10s
        val now2 = Clock.System.now().toEpochMilliseconds()
        h.player.skipNext()
        runCurrent()

        assertEquals(trackAuto, quickSkippedItem)
        assertEquals(true, quickSkippedWasAuto)
        assertEquals(1, autoQuickSkipCount, "Auto quick skip must occur exactly once")
        assertEquals(1, manualQuickSkipCount, "Manual quick skip must occur exactly once")
        assertTrue(repeatPolicy.isRested(trackAuto.repeatKey, now2), "PlayerImpl must synchronously rest auto-picked track")
        val restUntil = repeatPolicy.restUntil(trackAuto.repeatKey, now2)
        assertNotNull(restUntil)
        assertTrue(restUntil >= now2 + 3500_000L) // ~1 hour rest

        // Next item is trackNext, NEVER the rested trackAuto
        assertEquals("track_next", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
        assertNotEquals("track_auto", (h.player.queue.value.currentItem as? PlayableItem.YtMedia)?.videoId)
    }
}
