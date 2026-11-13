package io.ymusic.player.repeat

import io.ymusic.player.PlayableItem

interface RepeatPolicy {
    /** True once the snapshot is loaded from persistent storage. Filtering stays OFF until ready. */
    val isReady: Boolean

    /** Populates the in-memory snapshot of event history from storage. Safe across threads. */
    fun initSnapshot(snapshot: Map<String, List<RepeatEvent>>)

    /** Records a play/rewind/manual-replay event immediately into the in-memory snapshot. */
    fun recordEvent(key: String, playedAt: Long, weight: Double)
    fun recordEvent(key: String, canonicalKey: String?, playedAt: Long, weight: Double) = recordEvent(key, playedAt, weight)

    /** Records that [key] was played automatically, pushing it into the recent plays ring buffer and session. */
    fun recordAutoPlay(key: String)
    fun recordAutoPlay(key: String, canonicalKey: String?) = recordAutoPlay(key)

    /** Preloads recent played keys from database on startup to activate repetition defense immediately. */
    fun preloadRecentPlays(keys: List<String>) {}

    /** Returns true if [key] has any recorded event in the past [windowMs] before [now]. */
    fun hasEventInWindow(key: String, windowMs: Long, now: Long): Boolean
    fun hasEventInWindow(key: String, canonicalKey: String?, windowMs: Long, now: Long): Boolean = hasEventInWindow(key, windowMs, now)

    /** Sets a rest-until timestamp for [key]. */
    fun restItem(key: String, restUntil: Long)

    /** Clears active rest for [key], nulling restUntil without deleting play history. */
    fun clearRest(key: String)
    fun clearRest(key: String, canonicalKey: String?) = clearRest(key)

    /** Returns true if [key] is currently rested at [now]. */
    fun isRested(key: String, now: Long): Boolean
    fun isRested(key: String, canonicalKey: String?, now: Long): Boolean = isRested(key, now)

    /** Returns the active rest timestamp for [key] if any and not expired at [now]. */
    fun restUntil(key: String, now: Long): Long?

    /** True if the policy holds any events, active rests, or recent plays in memory. */
    val hasHistory: Boolean

    /** Clears all in-memory events, active rests, and recent plays. Safe across threads. */
    fun clearAll()

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

    /**
     * Filters [candidates] according to fatigue, cooldown, and rest, relaxing through
     * ladder levels until eligible candidates are found.
     */
    fun filterEligibleCandidates(
        candidates: List<Int>,
        items: List<PlayableItem>,
        noRepeatHours: Int,
        isExempt: (PlayableItem) -> Boolean = { false },
        now: Long,
        onFallbackNotice: (() -> Unit)? = null,
    ): List<Int> = candidates
}
