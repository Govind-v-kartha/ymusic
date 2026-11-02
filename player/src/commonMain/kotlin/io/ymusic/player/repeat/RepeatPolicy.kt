package io.ymusic.player.repeat

import io.ymusic.player.PlayableItem

interface RepeatPolicy {
    /** True once the snapshot is loaded from persistent storage. Filtering stays OFF until ready. */
    val isReady: Boolean

    /** Populates the in-memory snapshot of event history from storage. Safe across threads. */
    fun initSnapshot(snapshot: Map<String, List<RepeatEvent>>)

    /** Records a play/rewind/manual-replay event immediately into the in-memory snapshot. */
    fun recordEvent(key: String, playedAt: Long, weight: Double)

    /** Records that [key] was played automatically, pushing it into the recent plays ring buffer. */
    fun recordAutoPlay(key: String)

    /** Returns true if [key] has any recorded event in the past [windowMs] before [now]. */
    fun hasEventInWindow(key: String, windowMs: Long, now: Long): Boolean

    /**
     * Filters and selects the next item index from [candidates].
     *
     * @param candidates Ordered candidate indices into [items]
     * @param items Current queue items
     * @param currentIndex Current playing index
     * @param noRepeatHours Hours window setting (0 = Off: fatigue disabled, only count gap active)
     * @param allowScoreReordering Whether candidates may be reordered by score (true for shuffle/radio/autoplay)
     * @param isExempt Predicate for items exempt from filtering (e.g. upNext / "play next")
     * @param now Wall-clock timestamp in ms
     * @param onFallbackNotice Non-blocking notice callback when ladder reaches Level 4 or 5
     * @return Selected candidate index, or null if candidates is empty
     */
    fun selectNextIndex(
        candidates: List<Int>,
        items: List<PlayableItem>,
        currentIndex: Int,
        noRepeatHours: Int,
        allowScoreReordering: Boolean,
        isExempt: (PlayableItem) -> Boolean = { false },
        now: Long,
        onFallbackNotice: (() -> Unit)? = null,
    ): Int?
}
