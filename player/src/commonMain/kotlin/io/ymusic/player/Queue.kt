package io.ymusic.player

import io.ymusic.player.repeat.RepeatPolicy
import kotlin.random.Random
import kotlin.time.Clock

enum class LoopMode {
    /** Stop at the end of the queue. */
    None,

    /** Replay the current item when it ends (a user skip still moves on). */
    One,

    /** Wrap around at the ends of the queue. */
    All,
}

/**
 * The play queue as observed on [Player.queue]. [currentIndex] is `-1` when nothing is queued.
 * [queueId] names the queue the last [Player.setQueue] made: it survives add/move/remove and
 * changes only on the next setQueue; null when nothing is queued. [more] is set when the queue
 * has a [QueueSource] to grow from.
 */
data class QueueState(
    val items: List<PlayableItem> = emptyList(),
    val currentIndex: Int = -1,
    val loopMode: LoopMode = LoopMode.None,
    val shuffle: Boolean = false,
    val queueId: String? = null,
    val more: QueueMore? = null,
) {
    val currentItem: PlayableItem? get() = items.getOrNull(currentIndex)
}

/**
 * Where a queue's further items come from — a playlist's next pages. The player asks for a page
 * when the queue runs low (in order), when a shuffle pick lands on the not-yet-loaded part, and on
 * [Player.loadMore]. Calls never overlap.
 */
interface QueueSource {
    /** The whole list's length, loaded or not, when the source knows it; weighs shuffle picks. Read
     *  again after every page, so a source may learn it from its first page. */
    val totalCount: Int?

    /** Opaque state the app can store to rebuild this source later (where the list continues);
     *  null when it can't be resumed. Read after every page. */
    val resumeToken: String? get() = null

    /** The next page. Throws on failure; the player keeps the error in [QueueMore.error]. */
    suspend fun loadMore(): Page

    /** [items] may be empty (a page of duplicates) while [hasMore] is still true. */
    class Page(val items: List<PlayableItem>, val hasMore: Boolean)
}

/** The [QueueSource] side of [QueueState]: [totalCount] becomes the loaded size once it ends;
 *  [resumeToken] is the source's latest [QueueSource.resumeToken]. */
data class QueueMore(
    val totalCount: Int? = null,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val resumeToken: String? = null,
)

private const val MAX_PLAY_HISTORY = 100

/**
 * Pure queue/shuffle/loop logic — no coroutines, no engines. The facade calls a mutation, reads the
 * new [state], and decides whether to (re)load. Play history stack tracks reverse-order playback across
 * all modes (linear, shuffle, radio, autoplay), and index remapping keeps it valid across add/move/remove.
 */
internal class QueueController(
    private val random: Random = Random.Default,
    var repeatPolicy: RepeatPolicy? = null,
    var mixPolicy: io.ymusic.player.mix.MixPolicy? = null,
    var noRepeatHours: () -> Int = { 0 },
    var isRadioOrAutoplay: () -> Boolean = { false },
    var onFallbackNotice: (() -> Unit)? = null,
) {
    var state: QueueState = QueueState()
        private set

    /** Chronological stack of played indices across all modes (bounded to [MAX_PLAY_HISTORY]). Last is current. */
    private val playHistory = mutableListOf<Int>()

    /** Items played in the current shuffle cycle. Cleared on shuffle toggle and cycle exhaustion. */
    private val shufflePlayed = mutableSetOf<Int>()

    /** Indices queued by [addNext], played in order before any shuffle pick; a jump, move or remove clears it. */
    private val upNext = mutableListOf<Int>()

    val hasUpNext: Boolean get() = upNext.isNotEmpty()

    fun setQueue(items: List<PlayableItem>, playIndex: Int, queueId: String? = null) {
        val index = if (items.isEmpty()) -1 else playIndex.coerceIn(0, items.lastIndex)
        state = state.copy(items = items, currentIndex = index, queueId = queueId.takeIf { index >= 0 })
        playHistory.clear()
        shufflePlayed.clear()
        upNext.clear()
        if (index >= 0) {
            playHistory += index
            shufflePlayed += index
            items.getOrNull(index)?.let { mixPolicy?.onManualPlay(it) }
        }
    }

    fun setLoopMode(mode: LoopMode) {
        state = state.copy(loopMode = mode)
    }

    /** Toggling shuffle restarts the played history from the current item. */
    fun setShuffle(enabled: Boolean) {
        state = state.copy(shuffle = enabled)
        shufflePlayed.clear()
        if (state.currentIndex >= 0) shufflePlayed += state.currentIndex
    }

    /** Items not yet played in the current shuffle cycle. */
    fun unplayedCount(): Int = (state.items.indices - shufflePlayed).size

    /** Jump to [index]. False if out of bounds. */
    fun moveTo(index: Int): Boolean {
        if (index !in state.items.indices) return false
        upNext.clear()
        val item = state.items.getOrNull(index)
        advanceTo(index)
        if (item != null) {
            mixPolicy?.onManualPlay(item)
        }
        return true
    }

    /**
     * The next index after the current one ends ([userInitiated] = false) or is skipped (= true),
     * without changing state. Null when the queue is exhausted. The facade peeks this to preload,
     * then [commitNext] / [advance] to actually move.
     */
    fun peekNext(userInitiated: Boolean): Int? {
        val items = state.items
        if (items.isEmpty()) return null
        if (!userInitiated && state.loopMode == LoopMode.One) return state.currentIndex
        upNext.firstOrNull()?.let { return it }
        val policy = repeatPolicy
        if (policy == null || !policy.isReady) {
            return if (state.shuffle) peekShuffled() else nextLinear()
        }
        val candidates: List<Int> = if (state.shuffle || isRadioOrAutoplay()) {
            val unplayed = items.indices - shufflePlayed
            if (unplayed.isNotEmpty()) unplayed.toList()
            else if (items.size > 1) (items.indices - state.currentIndex).toList()
            else items.indices.toList()
        } else {
            val nextList = mutableListOf<Int>()
            for (i in (state.currentIndex + 1) until items.size) {
                nextList.add(i)
            }
            if (state.loopMode == LoopMode.All) {
                for (i in 0 until state.currentIndex) {
                    nextList.add(i)
                }
            }
            nextList
        }
        if (candidates.isEmpty()) return null

        val allowScoreReordering = state.shuffle || isRadioOrAutoplay()
        val now = Clock.System.now().toEpochMilliseconds()

        val mix = mixPolicy
        if (mix != null && mix.isEnabled && isRadioOrAutoplay()) {
            val eligible = policy.filterEligibleCandidates(
                candidates = candidates,
                items = items,
                noRepeatHours = noRepeatHours(),
                isExempt = { false },
                now = now,
                onFallbackNotice = onFallbackNotice,
            )
            val mixPick = mix.selectNextCandidate(eligible, items, state.currentIndex).selectedIndex
            if (mixPick != null) return mixPick
        }

        return policy.selectNextIndex(
            candidates = candidates,
            items = items,
            currentIndex = state.currentIndex,
            noRepeatHours = noRepeatHours(),
            allowScoreReordering = allowScoreReordering,
            isExempt = { false },
            now = now,
            onFallbackNotice = onFallbackNotice,
        )
    }

    /** Make [index] (from [peekNext]) the current item, updating shuffle history. */
    fun commitNext(index: Int) {
        val unplayed = state.items.indices - shufflePlayed
        if (state.shuffle && unplayed.isEmpty()) shufflePlayed.clear()
        val isExplicitUpNext = upNext.firstOrNull() == index
        val item = state.items.getOrNull(index)
        advanceTo(index)
        if (!isExplicitUpNext && item != null) {
            repeatPolicy?.recordAutoPlay(item.repeatKey, item.canonicalKey)
            mixPolicy?.onAutoPick(item)
        }
    }

    /**
     * The next item after the current one ends ([userInitiated] = false) or is skipped (= true).
     * Returns the new current index, or null when the queue is exhausted (state is unchanged then).
     */
    fun advance(userInitiated: Boolean): Int? {
        val next = peekNext(userInitiated) ?: return null
        commitNext(next)
        return next
    }

    /**
     * The item to go back to: the previously-played item in play history across all modes,
     * falling back to linear predecessor / wrap if history is exhausted.
     */
    fun previous(): Int? {
        val items = state.items
        if (items.isEmpty()) return null
        if (playHistory.size >= 2) {
            playHistory.removeAt(playHistory.lastIndex)
            val target = playHistory.last()
            if (target in items.indices) {
                state = state.copy(currentIndex = target)
                return target
            }
        }
        if (state.shuffle) return null
        val prev = when {
            state.currentIndex > 0 -> state.currentIndex - 1
            state.loopMode == LoopMode.All -> items.lastIndex
            else -> return null
        }
        state = state.copy(currentIndex = prev)
        playHistory.clear()
        playHistory += prev
        return prev
    }

    fun add(newItems: List<PlayableItem>, at: Int? = null) {
        if (newItems.isEmpty()) return
        val items = state.items.toMutableList()
        val index = (at ?: items.size).coerceIn(0, items.size)
        items.addAll(index, newItems)
        val shift = { i: Int -> if (i >= index) i + newItems.size else i }
        remap(shift)
        state = state.copy(items = items, currentIndex = shift(state.currentIndex).coerceAtLeast(-1))
    }

    fun addNext(newItems: List<PlayableItem>) {
        if (newItems.isEmpty()) return
        if (state.currentIndex < 0) return add(newItems)
        val at = (upNext.lastOrNull() ?: state.currentIndex) + 1
        add(newItems, at)
        upNext += newItems.indices.map { at + it }
    }

    /** Removes [at]; true when the removed item was the current one (the caller must reload/stop). */
    fun removeAt(at: Int): Boolean {
        if (at !in state.items.indices) return false
        val wasCurrent = at == state.currentIndex
        val items = state.items.toMutableList().also { it.removeAt(at) }
        upNext.clear()
        shufflePlayed.remove(at)
        playHistory.removeAll { it == at }
        remap { i -> if (i > at) i - 1 else i }
        val newCurrent = when {
            items.isEmpty() -> -1
            wasCurrent -> at.coerceAtMost(items.lastIndex)
            state.currentIndex > at -> state.currentIndex - 1
            else -> state.currentIndex
        }
        state = state.copy(items = items, currentIndex = newCurrent)
        if (wasCurrent && newCurrent >= 0) {
            shufflePlayed += newCurrent
            if (playHistory.isEmpty() || playHistory.last() != newCurrent) {
                playHistory += newCurrent
                if (playHistory.size > MAX_PLAY_HISTORY) playHistory.removeAt(0)
            }
        }
        return wasCurrent
    }

    fun move(from: Int, to: Int) {
        if (from !in state.items.indices || to !in state.items.indices || from == to) return
        upNext.clear()
        moveItem(from, to)
    }

    /** Moves [from] behind the songs already queued by [addNext] and queues it with them. */
    fun moveNext(from: Int) {
        val current = state.currentIndex
        if (from !in state.items.indices || current < 0 || from == current) return
        val queued = upNext.filter { it != from }
        val tail = queued.lastOrNull() ?: current
        val to = if (from > tail) tail + 1 else tail
        upNext.clear()
        val shift = if (from == to) { i: Int -> i } else moveItem(from, to)
        upNext += queued.map(shift)
        upNext += to
    }

    private fun moveItem(from: Int, to: Int): (Int) -> Int {
        val items = state.items.toMutableList()
        items.add(to, items.removeAt(from))
        val shift = { i: Int ->
            when {
                i == from -> to
                from < to && i in (from + 1)..to -> i - 1
                to < from && i in to until from -> i + 1
                else -> i
            }
        }
        remap(shift)
        state = state.copy(items = items, currentIndex = shift(state.currentIndex))
        return shift
    }

    /**
     * Removes the `[from, to)` range; true when the current item was removed. STRICT: an invalid
     * range throws — an app-side caller with bad indices is a bug to surface early, and boundary
     * callers relaying cross-process edits against a possibly-stale snapshot (the media3 adapter)
     * clamp BEFORE calling. Composed from [removeAt] so the played-history/remap bookkeeping
     * stays single-sourced. An empty (from == to) range is a valid no-op.
     */
    fun removeRange(from: Int, to: Int): Boolean {
        require(from in 0..to && to <= state.items.size) {
            "removeRange($from, $to) on ${state.items.size} items"
        }
        var removedCurrent = false
        repeat(to - from) { removedCurrent = removeAt(from) || removedCurrent }
        return removedCurrent
    }

    /**
     * Moves the `[from, to)` block so it starts at [newIndex] — [newIndex] in post-removal
     * indexing, media3's moveMediaItems contract. Same strict policy as [removeRange]; composed
     * from single [move]s (orderings verified for both directions).
     */
    fun moveRange(from: Int, to: Int, newIndex: Int) {
        val size = state.items.size
        require(from in 0..to && to <= size) { "moveRange($from, $to, $newIndex) on $size items" }
        val count = to - from
        require(newIndex in 0..(size - count)) { "moveRange($from, $to, $newIndex) on $size items" }
        when {
            count == 0 || newIndex == from -> Unit
            newIndex < from -> repeat(count) { i -> move(from + i, newIndex + i) }
            else -> repeat(count) { move(from, newIndex + count - 1) }
        }
    }

    fun clear() {
        state = state.copy(items = emptyList(), currentIndex = -1, queueId = null)
        playHistory.clear()
        shufflePlayed.clear()
        upNext.clear()
    }

    private fun advanceTo(index: Int) {
        state = state.copy(currentIndex = index)
        upNext.remove(index)
        shufflePlayed += index
        if (playHistory.isEmpty() || playHistory.last() != index) {
            playHistory += index
            if (playHistory.size > MAX_PLAY_HISTORY) {
                playHistory.removeAt(0)
            }
        }
    }

    private fun nextLinear(): Int? {
        val next = state.currentIndex + 1
        return when {
            next <= state.items.lastIndex -> next
            state.loopMode == LoopMode.All -> 0
            else -> null
        }
    }

    private fun peekShuffled(): Int? {
        val all = state.items.indices
        val unplayed = all - shufflePlayed
        if (unplayed.isNotEmpty()) return unplayed.elementAt(random.nextInt(unplayed.size))
        if (state.loopMode != LoopMode.All) return null
        // Every item played: restart the shuffle cycle, avoiding an immediate repeat when possible.
        val candidates = if (state.items.size > 1) all - state.currentIndex else all.toList()
        return candidates.elementAt(random.nextInt(candidates.count()))
    }

    private fun remap(shift: (Int) -> Int) {
        for (i in playHistory.indices) playHistory[i] = shift(playHistory[i])
        val remappedShuffle = shufflePlayed.map(shift).toMutableSet()
        shufflePlayed.clear()
        shufflePlayed.addAll(remappedShuffle)
        for (i in upNext.indices) upNext[i] = shift(upNext[i])
    }
}
