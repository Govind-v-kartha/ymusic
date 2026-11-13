package io.ymusic.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueControllerTest {
    private fun items(n: Int) = (0 until n).map { PlayableItem.LocalMedia("file:///$it.mp3") }

    private fun controller(n: Int, playIndex: Int = 0, seed: Int = 1) =
        QueueController(Random(seed)).apply { setQueue(items(n), playIndex) }

    @Test
    fun removeRange_is_strict_and_reports_current() {
        val q = controller(5, playIndex = 2)
        // Strict contract: bad indices are an app-side bug, not something to degrade silently —
        // the cross-process boundary (media3 adapter) clamps BEFORE calling.
        assertFailsWith<IllegalArgumentException> { q.removeRange(2, 99) }
        assertFailsWith<IllegalArgumentException> { q.removeRange(3, 2) }
        assertTrue(q.removeRange(2, 5), "the current item was inside the removed range")
        assertEquals(items(5).take(2), q.state.items)
        assertEquals(1, q.state.currentIndex, "current lands on the last remaining item")
        assertFalse(q.removeRange(1, 1), "an empty range is a valid no-op")
    }

    @Test
    fun moveRange_moves_blocks_both_directions() {
        fun uris(q: QueueController) = q.state.items.map { (it as PlayableItem.LocalMedia).uri }
        fun expect(vararg i: Int) = i.map { "file:///$it.mp3" }

        val q = controller(5) // [0 1 2 3 4]
        q.moveRange(1, 3, 3) // block [1,2] to start at 3 (post-removal indexing)
        assertEquals(expect(0, 3, 4, 1, 2), uris(q))
        q.moveRange(3, 5, 0) // and back to the front
        assertEquals(expect(1, 2, 0, 3, 4), uris(q))
        assertFailsWith<IllegalArgumentException> { q.moveRange(4, 99, 0) }
        assertFailsWith<IllegalArgumentException> { q.moveRange(1, 3, 4) } // target past size - count
        assertEquals(expect(1, 2, 0, 3, 4), uris(q), "failed edits must not half-apply")
    }

    @Test
    fun peek_next_does_not_move_current() {
        val q = controller(3)
        assertEquals(1, q.peekNext(userInitiated = false))
        assertEquals(0, q.state.currentIndex)
        q.commitNext(1)
        assertEquals(1, q.state.currentIndex)
        assertNull(controller(1).peekNext(userInitiated = false), "no loop: last item has no next")
    }

    @Test
    fun linear_advance_and_exhaustion() {
        val q = controller(3)
        assertEquals(1, q.advance(userInitiated = false))
        assertEquals(2, q.advance(userInitiated = false))
        assertNull(q.advance(userInitiated = false))
        assertEquals(2, q.state.currentIndex, "exhausted advance must not move")
    }

    @Test
    fun loop_all_wraps_both_directions() {
        val q = controller(3, playIndex = 2)
        q.setLoopMode(LoopMode.All)
        assertEquals(0, q.advance(userInitiated = false))
        assertEquals(2, q.previous(), "previous from 0 wraps under All")
    }

    @Test
    fun loop_one_repeats_on_ended_but_not_on_user_skip() {
        val q = controller(3, playIndex = 1)
        q.setLoopMode(LoopMode.One)
        assertEquals(1, q.advance(userInitiated = false))
        assertEquals(2, q.advance(userInitiated = true))
    }

    @Test
    fun shuffle_plays_every_item_once_before_stopping() {
        val q = controller(5)
        q.setShuffle(true)
        val seen = mutableSetOf(q.state.currentIndex)
        repeat(4) { seen += q.advance(userInitiated = false) ?: error("exhausted too early") }
        assertEquals((0..4).toSet(), seen, "shuffle must cover the whole queue")
        assertNull(q.advance(userInitiated = false), "no loop: exhausted after full coverage")
    }

    @Test
    fun shuffle_with_loop_all_restarts_without_immediate_repeat() {
        val q = controller(4)
        q.setShuffle(true)
        q.setLoopMode(LoopMode.All)
        repeat(3) { q.advance(userInitiated = false) }
        val last = q.state.currentIndex
        val next = q.advance(userInitiated = false)
        assertTrue(next != null && next != last, "cycle restart must avoid an immediate repeat")
    }

    @Test
    fun shuffle_previous_walks_played_history() {
        val q = controller(5)
        q.setShuffle(true)
        val first = q.state.currentIndex
        val second = q.advance(userInitiated = false)!!
        val third = q.advance(userInitiated = false)!!
        assertEquals(second, q.previous())
        assertEquals(first, q.previous())
        assertNull(q.previous(), "history exhausted")
        // Forward again re-records history from here.
        val again = q.advance(userInitiated = false)!!
        assertEquals(first, q.previous(), "prev returns to where we branched, not $again")
    }

    @Test
    fun add_before_current_shifts_current() {
        val q = controller(3, playIndex = 1)
        q.add(listOf(PlayableItem.YtMedia("a"), PlayableItem.YtMedia("b")), at = 0)
        assertEquals(3, q.state.currentIndex)
        assertEquals(5, q.state.items.size)
    }

    @Test
    fun add_next_inserts_after_current() {
        val q = controller(3, playIndex = 1)
        val next = PlayableItem.YtMedia("abc")
        q.addNext(listOf(next))
        assertEquals(next, q.state.items[2])
        assertEquals(1, q.state.currentIndex)
        assertEquals(2, q.advance(userInitiated = false), "inserted item plays next")
    }

    @Test
    fun remove_current_keeps_slot_and_reports() {
        val q = controller(3, playIndex = 1)
        assertTrue(q.removeAt(1))
        assertEquals(1, q.state.currentIndex, "slot is kept — the following item takes it")
        assertEquals(2, q.state.items.size)
    }

    @Test
    fun remove_last_current_clamps() {
        val q = controller(2, playIndex = 1)
        assertTrue(q.removeAt(1))
        assertEquals(0, q.state.currentIndex)
    }

    @Test
    fun remove_before_current_shifts_current_down() {
        val q = controller(3, playIndex = 2)
        assertFalse(q.removeAt(0))
        assertEquals(1, q.state.currentIndex)
    }

    @Test
    fun remove_to_empty() {
        val q = controller(1)
        assertTrue(q.removeAt(0))
        assertEquals(-1, q.state.currentIndex)
        assertNull(q.state.currentItem)
    }

    @Test
    fun move_tracks_current_item() {
        val q = controller(4, playIndex = 1)
        val current = q.state.currentItem
        q.move(1, 3)
        assertEquals(3, q.state.currentIndex)
        assertEquals(current, q.state.currentItem)
        q.move(0, 3)
        assertEquals(2, q.state.currentIndex)
        assertEquals(current, q.state.currentItem)
    }

    @Test
    fun set_queue_clamps_play_index() {
        val q = QueueController(Random(1))
        q.setQueue(items(2), playIndex = 9)
        assertEquals(1, q.state.currentIndex)
        q.setQueue(emptyList(), playIndex = 0)
        assertEquals(-1, q.state.currentIndex)
    }

    @Test
    fun move_to_bounds_checked() {
        val q = controller(2)
        assertFalse(q.moveTo(5))
        assertTrue(q.moveTo(1))
        assertEquals(1, q.state.currentIndex)
    }

    @Test
    fun next_x3_then_previous_x3_returns_exact_earlier_songs() {
        val q = controller(6, playIndex = 0)
        assertEquals(1, q.advance(userInitiated = false))
        assertEquals(2, q.advance(userInitiated = false))
        assertEquals(3, q.advance(userInitiated = false))
        assertEquals(3, q.state.currentIndex)

        assertEquals(2, q.previous())
        assertEquals(1, q.previous())
        assertEquals(0, q.previous())
    }

    @Test
    fun previous_with_blocked_candidates_in_between() {
        val q = controller(6, playIndex = 0)
        var step = 0
        val fakePolicy = object : io.ymusic.player.repeat.RepeatPolicy {
            override val isReady: Boolean = true
            override val hasHistory: Boolean = false
            override fun initSnapshot(snapshot: Map<String, List<io.ymusic.player.repeat.RepeatEvent>>) {}
            override fun recordEvent(key: String, playedAt: Long, weight: Double) {}
            override fun recordAutoPlay(key: String) {}
            override fun hasEventInWindow(key: String, windowMs: Long, now: Long): Boolean = false
            override fun restItem(key: String, restUntil: Long) {}
            override fun clearRest(key: String) {}
            override fun isRested(key: String, now: Long): Boolean = false
            override fun restUntil(key: String, now: Long): Long? = null
            override fun clearAll() {}
            override fun selectNextIndex(
                candidates: List<Int>,
                items: List<PlayableItem>,
                currentIndex: Int,
                noRepeatHours: Int,
                allowScoreReordering: Boolean,
                isExempt: (PlayableItem) -> Boolean,
                now: Long,
                onFallbackNotice: (() -> Unit)?,
            ): Int? {
                step++
                return if (step == 1) 2 else 5 // Skip 1 -> pick 2, then skip 3,4 -> pick 5
            }
        }
        q.repeatPolicy = fakePolicy
        assertEquals(2, q.advance(userInitiated = false))
        assertEquals(5, q.advance(userInitiated = false))
        assertEquals(5, q.state.currentIndex)

        assertEquals(2, q.previous(), "must return 2 (skipping 4, 3)")
        assertEquals(0, q.previous(), "must return 0 (skipping 1)")
    }

    @Test
    fun previous_with_shuffle_on() {
        val q = controller(5, playIndex = 0, seed = 42)
        q.setShuffle(true)
        val p1 = q.advance(userInitiated = false)!!
        val p2 = q.advance(userInitiated = false)!!
        val p3 = q.advance(userInitiated = false)!!

        assertEquals(p2, q.previous())
        assertEquals(p1, q.previous())
        assertEquals(0, q.previous())
        assertNull(q.previous(), "exhausted history under shuffle returns null")
    }

    @Test
    fun previous_with_radio_refill_between() {
        val q = controller(2, playIndex = 0)
        assertEquals(1, q.advance(userInitiated = false))

        // Radio refills page of new items at the tail
        q.add(listOf(PlayableItem.LocalMedia("file:///2.mp3"), PlayableItem.LocalMedia("file:///3.mp3")))
        assertEquals(4, q.state.items.size)
        assertEquals(1, q.state.currentIndex)

        assertEquals(2, q.advance(userInitiated = false))
        assertEquals(2, q.state.currentIndex)

        assertEquals(1, q.previous(), "previous crosses refill boundary back to original page")
        assertEquals(0, q.previous())
    }

    @Test
    fun previous_after_play_at() {
        val q = controller(6, playIndex = 0)
        assertTrue(q.moveTo(4))
        assertEquals(4, q.state.currentIndex)
        assertEquals(5, q.advance(userInitiated = false))
        assertEquals(5, q.state.currentIndex)

        assertEquals(4, q.previous())
        assertEquals(0, q.previous(), "previous returns to 0 before the playAt jump")
    }

    @Test
    fun previous_after_insert_items_at_index() {
        // Queue: [0, 1, 2]. Play 0 -> advance to 1. History: [0, 1]
        val q = controller(3, playIndex = 0)
        assertEquals(1, q.advance(userInitiated = false))

        // Insert 2 items at index 1: old items [1, 2] shift to [3, 4]
        // Currently playing item was at 1, now at 3. History: [0, 3]
        q.add(listOf(PlayableItem.LocalMedia("file:///ins1.mp3"), PlayableItem.LocalMedia("file:///ins2.mp3")), at = 1)
        assertEquals(5, q.state.items.size)
        assertEquals(3, q.state.currentIndex)

        // Previous should return 0 (the earlier played song), not the newly inserted item at 2 or 1
        assertEquals(0, q.previous())
    }

    @Test
    fun previous_after_remove_at_played_and_unplayed() {
        // Queue: [0, 1, 2, 3]. Play 0 -> advance 1 -> advance 2. History: [0, 1, 2]
        val q = controller(4, playIndex = 0)
        assertEquals(1, q.advance(userInitiated = false))
        assertEquals(2, q.advance(userInitiated = false))

        // Remove item at index 1 (which was played). History should remove 1 and shift 2 to 1 -> [0, 1]
        q.removeAt(1)
        assertEquals(3, q.state.items.size)
        assertEquals(1, q.state.currentIndex)

        // Previous from current (old 2, now 1) should return 0 (skipping deleted 1)
        assertEquals(0, q.previous())
    }

    @Test
    fun previous_after_move_reordering() {
        // Queue: [0, 1, 2, 3]. Play 0 -> advance 1. History: [0, 1]
        val q = controller(4, playIndex = 0)
        assertEquals(1, q.advance(userInitiated = false))

        // Move currently playing item 1 to end (index 3). History [0, 1] becomes [0, 3]
        q.move(1, 3)
        assertEquals(3, q.state.currentIndex)

        // Advance to next track (linear 3 wraps or next; let's moveTo 2)
        assertEquals(0, q.previous(), "previous from 3 returns 0 (the previous track in history)")
    }

    @Test
    fun previous_after_add_next() {
        // Queue: [0, 1, 2]. Play 0. Add 2 items to play next
        val q = controller(3, playIndex = 0)
        q.addNext(listOf(PlayableItem.LocalMedia("file:///next1.mp3"), PlayableItem.LocalMedia("file:///next2.mp3")))
        // Items inserted at index 1 and 2. Old 1 and 2 become 3 and 4
        // Advance to first play-next track (index 1)
        assertEquals(1, q.advance(userInitiated = false))
        assertEquals(1, q.state.currentIndex)

        // Previous from play-next track returns 0 (the original track)
        assertEquals(0, q.previous())
    }

    @Test
    fun previous_after_clear_and_set_queue() {
        val q = controller(3, playIndex = 0)
        assertEquals(1, q.advance(userInitiated = false))
        q.clear()
        assertNull(q.previous())

        q.setQueue(listOf(PlayableItem.LocalMedia("file:///new0.mp3"), PlayableItem.LocalMedia("file:///new1.mp3")), 1)
        assertEquals(1, q.state.currentIndex)
        // History only has 1, so linear predecessor returns 0
        assertEquals(0, q.previous())
    }
}
