package io.ymusic.player

import io.ymusic.player.repeat.RepeatConfig
import io.ymusic.player.repeat.RepeatEvent
import io.ymusic.player.repeat.RepeatPolicyImpl
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RepeatPolicyTest {
    private fun ytItem(id: String, artist: String? = null) = PlayableItem.YtMedia(
        videoId = id,
        title = "Title $id",
        artist = artist,
        durationMs = 180_000L,
    )

    private fun localItem(uri: String, artist: String? = null, playlistCode: String? = null) = PlayableItem.LocalMedia(
        uri = uri,
        title = "Title $uri",
        artist = artist,
        playlistCode = playlistCode,
        durationMs = 180_000L,
    )

    @Test
    fun count_gap_blocks_item_3_to_5_plays_ago_even_when_off() {
        val policy = RepeatPolicyImpl(Random(42))
        policy.initSnapshot(emptyMap())

        // 15 items in queue
        val items = (0 until 15).map { ytItem("vid_$it") }
        val candidates = (0 until 15).toList()

        // Record 12 auto-plays sequentially: vid_0 to vid_11
        for (i in 0..11) {
            policy.recordAutoPlay("vid_$i")
        }

        // Current ring buffer has last 12 auto-plays (capacity 20).
        // Default minGap is 10.
        // Recent 10 are: vid_2, vid_3, ..., vid_11.
        // vid_8 is 3 plays ago (from vid_11).
        // vid_6 is 5 plays ago.
        // Both vid_8 and vid_6 should be blocked even when noRepeatHours = 0 (Off).

        // Candidate vid_8 (index 8) alone:
        val fallbackNoticeCalled = booleanArrayOf(false)
        val selectedIndex = policy.selectNextIndex(
            candidates = listOf(8),
            items = items,
            currentIndex = 11,
            noRepeatHours = 0, // Off
            allowScoreReordering = false,
            now = 100_000L,
            onFallbackNotice = { fallbackNoticeCalled[0] = true },
        )

        // Since index 8 is the ONLY candidate and blocked by minGap 10, it drops through ladder
        // (ladder relaxes minGap to 5 -> still in last 5 -> relaxes hours -> oldest played)
        assertEquals(8, selectedIndex)
        assertTrue(fallbackNoticeCalled[0], "Fallback notice should trigger for all-blocked candidates")

        // In a pool with non-recent candidates [8, 12, 13, 14]:
        // vid_8 is in recent 10, but vid_12 is not.
        val pick = policy.selectNextIndex(
            candidates = listOf(8, 12, 13, 14),
            items = items,
            currentIndex = 11,
            noRepeatHours = 0,
            allowScoreReordering = false,
            now = 100_000L,
        )
        // Linear mode keeps order: skips blocked vid_8 and selects first eligible vid_12!
        assertEquals(12, pick)
    }

    @Test
    fun heavy_replay_increases_rest_time_compared_with_single_play() {
        val policy = RepeatPolicyImpl(Random(42))
        val now = 1_000_000L
        val tauHours = 2
        val tauMs = tauHours * 3600 * 1000.0 // 7,200,000 ms

        // Single play at t = now
        policy.initSnapshot(
            mapOf(
                "single" to listOf(RepeatEvent(playedAt = now, weight = RepeatConfig.WEIGHT_QUALIFYING)),
                "heavy" to listOf(
                    RepeatEvent(playedAt = now, weight = RepeatConfig.WEIGHT_QUALIFYING),
                    RepeatEvent(playedAt = now, weight = RepeatConfig.WEIGHT_QUALIFYING),
                    RepeatEvent(playedAt = now, weight = RepeatConfig.WEIGHT_QUALIFYING),
                ),
            ),
        )

        // At t = now:
        // single fatigue = 1.0 (at threshold, not < threshold)
        // heavy fatigue = 3.0 (way above threshold)
        assertEquals(1.0, policy.calculateFatigue("single", now, tauHours))
        assertEquals(3.0, policy.calculateFatigue("heavy", now, tauHours))

        // Time after 1 hour (3,600,000 ms = 0.5 * tauMs):
        val t1h = now + 3_600_000L
        val singleFatigue1h = policy.calculateFatigue("single", t1h, tauHours)
        val heavyFatigue1h = policy.calculateFatigue("heavy", t1h, tauHours)

        assertTrue(singleFatigue1h < RepeatConfig.FATIGUE_THRESHOLD, "Single play should be eligible after 1h")
        assertTrue(heavyFatigue1h > RepeatConfig.FATIGUE_THRESHOLD, "Heavy replay should still be fatigue-blocked after 1h")

        // Heavy replay needs t > tau * ln(3) ~ 7,200,000 * 1.0986 = ~7,910,000 ms
        val requiredRestMs = (tauMs * ln(3.0)).toLong() + 1000L
        val heavyRecovered = policy.calculateFatigue("heavy", now + requiredRestMs, tauHours)
        assertTrue(heavyRecovered < RepeatConfig.FATIGUE_THRESHOLD, "Heavy replay should recover after tau * ln(3)")
    }

    @Test
    fun rewind_adds_half_weight_in_fatigue() {
        val policy = RepeatPolicyImpl(Random(42))
        val now = 500_000L
        policy.initSnapshot(emptyMap())

        policy.recordEvent("vid_1", now, RepeatConfig.WEIGHT_QUALIFYING) // 1.0
        policy.recordEvent("vid_1", now, RepeatConfig.WEIGHT_REWIND)     // 0.5

        val fatigue = policy.calculateFatigue("vid_1", now, 2)
        assertEquals(1.5, fatigue)
    }

    @Test
    fun snapshot_not_loaded_filtering_stays_off() {
        val policy = RepeatPolicyImpl(Random(42))
        assertFalse(policy.isReady)

        val items = listOf(ytItem("vid_0"), ytItem("vid_1"))
        // Even if vid_0 was auto-played:
        policy.recordAutoPlay("vid_0")

        val pick = policy.selectNextIndex(
            candidates = listOf(0, 1),
            items = items,
            currentIndex = 0,
            noRepeatHours = 2,
            allowScoreReordering = true,
            now = 100_000L,
        )
        // Filtering is OFF when not ready, returns candidates.first() immediately without blocking
        assertEquals(0, pick)
    }

    @Test
    fun queue_of_one_item_fallback_never_stalls() {
        val policy = RepeatPolicyImpl(Random(42))
        policy.initSnapshot(emptyMap())

        val items = listOf(ytItem("vid_single"))
        policy.recordAutoPlay("vid_single")

        var noticeTriggered = false
        val pick = policy.selectNextIndex(
            candidates = listOf(0),
            items = items,
            currentIndex = 0,
            noRepeatHours = 4,
            allowScoreReordering = true,
            now = 100_000L,
            onFallbackNotice = { noticeTriggered = true },
        )

        assertEquals(0, pick)
        assertTrue(noticeTriggered, "Notice callback triggered when all candidates exhausted")
    }

    @Test
    fun linear_mode_keeps_original_order() {
        val policy = RepeatPolicyImpl(Random(42))
        policy.initSnapshot(
            mapOf(
                // vid_2 has highest affinity (3 qualifying plays)
                "vid_2" to listOf(RepeatEvent(0L, 1.0), RepeatEvent(0L, 1.0), RepeatEvent(0L, 1.0)),
            ),
        )

        val items = listOf(ytItem("vid_0"), ytItem("vid_1"), ytItem("vid_2"))
        val pick = policy.selectNextIndex(
            candidates = listOf(1, 2),
            items = items,
            currentIndex = 0,
            noRepeatHours = 0,
            allowScoreReordering = false, // Linear playlist
            now = 100_000L,
        )

        // In linear mode, candidate 1 is returned because it's first eligible in sequence (no score reordering)
        assertEquals(1, pick)
    }

    @Test
    fun shuffle_mode_uses_scoring() {
        val policy = RepeatPolicyImpl(Random(123))
        policy.initSnapshot(
            mapOf(
                // vid_1 has 0 events -> affinity 0
                // vid_2 has 3 events -> affinity 1.0
                "vid_2" to listOf(RepeatEvent(0L, 1.0), RepeatEvent(0L, 1.0), RepeatEvent(0L, 1.0)),
            ),
        )

        val items = listOf(ytItem("vid_0"), ytItem("vid_1"), ytItem("vid_2"))
        val pick = policy.selectNextIndex(
            candidates = listOf(1, 2),
            items = items,
            currentIndex = 0,
            noRepeatHours = 0,
            allowScoreReordering = true, // Shuffle mode
            now = 100_000L,
        )

        // In shuffle mode, candidate 2 has much higher score and wins
        assertEquals(2, pick)
    }

    @Test
    fun same_artist_penalty_is_zero_when_artist_missing() {
        val policy = RepeatPolicyImpl(Random(42))
        policy.initSnapshot(emptyMap())

        // Current item has no artist metadata
        val items = listOf(
            ytItem("vid_0", artist = null),
            ytItem("vid_1", artist = "Artist A"),
        )

        val pick = policy.selectNextIndex(
            candidates = listOf(1),
            items = items,
            currentIndex = 0,
            noRepeatHours = 0,
            allowScoreReordering = true,
            now = 100_000L,
        )

        assertEquals(1, pick)
    }

    @Test
    fun no_repeat_off_disables_fatigue_completely() {
        val policy = RepeatPolicyImpl(Random(42))
        val now = 1_000_000L

        // vid_1 played 1 second ago with massive weight
        policy.initSnapshot(
            mapOf("vid_1" to listOf(RepeatEvent(playedAt = now - 1000L, weight = 10.0))),
        )

        // When noRepeatHours = 0, fatigue is 0.0
        val fatigue = policy.calculateFatigue("vid_1", now, tauHours = 0)
        assertEquals(0.0, fatigue)

        // And if not in the last 10 auto-plays, it is eligible immediately
        val items = listOf(ytItem("vid_0"), ytItem("vid_1"))
        val pick = policy.selectNextIndex(
            candidates = listOf(1),
            items = items,
            currentIndex = 0,
            noRepeatHours = 0,
            allowScoreReordering = false,
            now = now,
        )
        assertEquals(1, pick)
    }

    @Test
    fun repeat_one_and_up_next_bypass_filtering_in_queue_controller() {
        val policy = RepeatPolicyImpl(Random(42))
        policy.initSnapshot(emptyMap())

        val q = QueueController(Random(42), repeatPolicy = policy)
        val items = listOf(ytItem("vid_0"), ytItem("vid_1"), ytItem("vid_2"))
        q.setQueue(items, playIndex = 0)

        // 1. Repeat-One loop mode:
        q.setLoopMode(LoopMode.One)
        // Non-user-initiated peekNext must return currentIndex (0)
        assertEquals(0, q.peekNext(userInitiated = false))

        // 2. Play next item:
        q.setLoopMode(LoopMode.None)
        q.addToPlayNext(listOf(ytItem("vid_play_next")))
        // Next peek must return the upNext item index (1)
        assertEquals(1, q.peekNext(userInitiated = false))

        // 3. User skip (next button):
        // userInitiated = true under LoopMode.One breaks out of single repeat
        q.setLoopMode(LoopMode.One)
        // With upNext cleared:
        q.moveTo(0)
        val nextOnSkip = q.peekNext(userInitiated = true)
        // Next track (1), not 0
        assertEquals(1, nextOnSkip)
    }

    @Test
    fun window_boundary_eligible_after_exact_tau_recovery() {
        val policy = RepeatPolicyImpl(Random(42))
        val now = 10_000_000L
        val tauHours = 1
        val tauMs = 3600 * 1000.0

        // Single play of weight 1.0
        policy.initSnapshot(
            mapOf("vid_test" to listOf(RepeatEvent(playedAt = now, weight = 1.0))),
        )

        // At exact play time (dt = 0): fatigue = 1.0 (ineligible, threshold is < 1.0)
        assertEquals(1.0, policy.calculateFatigue("vid_test", now, tauHours))

        // At dt = 1 millisecond later: exp(-1 / 3600000) = 0.9999997 < 1.0 (eligible!)
        val fatigueJustAfter = policy.calculateFatigue("vid_test", now + 1L, tauHours)
        assertTrue(fatigueJustAfter < 1.0)
    }
}
