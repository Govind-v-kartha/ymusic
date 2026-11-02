package io.ymusic.player.repeat

import io.ymusic.player.PlayableItem
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.math.exp
import kotlin.random.Random

class RepeatPolicyImpl(
    private val random: Random = Random.Default,
) : RepeatPolicy {
    @Volatile
    override var isReady: Boolean = false
        private set

    private val lock = SynchronizedObject()
    private val snapshot = mutableMapOf<String, MutableList<RepeatEvent>>()
    private val recentAutoPlays = ArrayDeque<String>(RepeatConfig.RECENT_BUFFER_CAPACITY)

    override fun initSnapshot(snapshot: Map<String, List<RepeatEvent>>) {
        synchronized(lock) {
            this.snapshot.clear()
            snapshot.forEach { (key, events) ->
                this.snapshot[key] = events.toMutableList()
            }
            isReady = true
        }
    }

    override fun recordEvent(key: String, playedAt: Long, weight: Double) {
        synchronized(lock) {
            val list = snapshot.getOrPut(key) { mutableListOf() }
            list.add(RepeatEvent(playedAt, weight))
        }
    }

    override fun recordAutoPlay(key: String) {
        synchronized(lock) {
            if (recentAutoPlays.size >= RepeatConfig.RECENT_BUFFER_CAPACITY) {
                recentAutoPlays.removeFirst()
            }
            recentAutoPlays.addLast(key)
        }
    }

    override fun hasEventInWindow(key: String, windowMs: Long, now: Long): Boolean {
        synchronized(lock) {
            val events = snapshot[key] ?: return false
            val minTime = now - windowMs
            return events.any { it.playedAt >= minTime }
        }
    }

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
        if (candidates.isEmpty()) return null
        if (!isReady) return candidates.first()

        val currentArtist = items.getOrNull(currentIndex)?.artistName?.trim()

        synchronized(lock) {
            // Level 0: Full policy (minGap = 10, fatigue active, artist penalty active)
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = RepeatConfig.MIN_GAP,
                checkFatigue = noRepeatHours > 0,
                tauHours = noRepeatHours,
                applyArtistPenalty = true,
                currentArtist = currentArtist,
                allowScoreReordering = allowScoreReordering,
                isExempt = isExempt,
                now = now,
            )?.let { return it }

            // Level 1: Drop artist penalty
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = RepeatConfig.MIN_GAP,
                checkFatigue = noRepeatHours > 0,
                tauHours = noRepeatHours,
                applyArtistPenalty = false,
                currentArtist = currentArtist,
                allowScoreReordering = allowScoreReordering,
                isExempt = isExempt,
                now = now,
            )?.let { return it }

            // Level 2: Drop scoring to plain order (no score reordering even in shuffle)
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = RepeatConfig.MIN_GAP,
                checkFatigue = noRepeatHours > 0,
                tauHours = noRepeatHours,
                applyArtistPenalty = false,
                currentArtist = currentArtist,
                allowScoreReordering = false,
                isExempt = isExempt,
                now = now,
            )?.let { return it }

            // Level 3: Reduce minGap to 5
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = RepeatConfig.MIN_GAP_RELAXED,
                checkFatigue = noRepeatHours > 0,
                tauHours = noRepeatHours,
                applyArtistPenalty = false,
                currentArtist = currentArtist,
                allowScoreReordering = false,
                isExempt = isExempt,
                now = now,
            )?.let { return it }

            // Level 4: Ignore hours window (fatigue disabled, minGap relaxed)
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = RepeatConfig.MIN_GAP_RELAXED,
                checkFatigue = false,
                tauHours = 0,
                applyArtistPenalty = false,
                currentArtist = currentArtist,
                allowScoreReordering = false,
                isExempt = isExempt,
                now = now,
            )?.let {
                onFallbackNotice?.invoke()
                return it
            }

            // Level 5: Oldest played item among candidates (never stall playback)
            onFallbackNotice?.invoke()
            return candidates.minByOrNull { idx ->
                val key = items[idx].repeatKey
                val events = snapshot[key]
                events?.maxOfOrNull { it.playedAt } ?: Long.MIN_VALUE
            } ?: candidates.first()
        }
    }

    private fun evaluateLadder(
        candidates: List<Int>,
        items: List<PlayableItem>,
        minGap: Int,
        checkFatigue: Boolean,
        tauHours: Int,
        applyArtistPenalty: Boolean,
        currentArtist: String?,
        allowScoreReordering: Boolean,
        isExempt: (PlayableItem) -> Boolean,
        now: Long,
    ): Int? {
        val recentGapSet = if (minGap > 0 && recentAutoPlays.isNotEmpty()) {
            recentAutoPlays.takeLast(minGap).toSet()
        } else {
            emptySet()
        }

        val eligible = mutableListOf<CandidateScored>()

        for (idx in candidates) {
            val item = items.getOrNull(idx) ?: continue
            if (isExempt(item)) {
                return idx // Explicitly queued items (play next) bypass everything
            }

            val key = item.repeatKey
            if (key in recentGapSet) continue

            val fatigue = if (checkFatigue && tauHours > 0) calculateFatigue(key, now, tauHours) else 0.0
            if (checkFatigue && fatigue >= RepeatConfig.FATIGUE_THRESHOLD) continue

            if (!allowScoreReordering) {
                // Linear / plain order: first eligible candidate wins immediately
                return idx
            }

            val affinity = calculateAffinity(key)
            val artistPenalty = if (applyArtistPenalty && !currentArtist.isNullOrEmpty()) {
                val candidateArtist = item.artistName?.trim()
                if (!candidateArtist.isNullOrEmpty() && candidateArtist.equals(currentArtist, ignoreCase = true)) {
                    RepeatConfig.SAME_ARTIST_PENALTY
                } else {
                    0.0
                }
            } else {
                0.0
            }

            val jitter = random.nextDouble(0.0, RepeatConfig.JITTER_MAX)
            val score = affinity.coerceIn(0.0, 1.0) - (1.5 * fatigue) - artistPenalty + jitter

            eligible.add(CandidateScored(idx, score))
        }

        return eligible.maxByOrNull { it.score }?.index
    }

    internal fun calculateFatigue(key: String, now: Long, tauHours: Int): Double {
        val events = snapshot[key] ?: return 0.0
        val tauMs = tauHours * 3600 * 1000.0
        var total = 0.0
        for (event in events) {
            val dt = (now - event.playedAt).coerceAtLeast(0L)
            total += event.weight * exp(-dt / tauMs)
        }
        return total
    }

    internal fun calculateAffinity(key: String): Double {
        val events = snapshot[key] ?: return 0.0
        val totalWeight = events.sumOf { it.weight }
        return (totalWeight / 3.0).coerceIn(0.0, 1.0)
    }

    private data class CandidateScored(val index: Int, val score: Double)
}
