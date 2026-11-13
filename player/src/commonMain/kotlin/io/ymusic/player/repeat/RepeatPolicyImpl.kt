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
    private val restedUntil = mutableMapOf<String, Long>()
    private val recentAutoPlays = ArrayDeque<String>(RepeatConfig.RECENT_BUFFER_CAPACITY)
    private val sessionKeys = mutableSetOf<String>()
    private var lastPlaybackTimeMs: Long = 0L
    private var pendingClear: Boolean = false

    override val hasHistory: Boolean
        get() = synchronized(lock) {
            snapshot.isNotEmpty() || restedUntil.isNotEmpty() || recentAutoPlays.isNotEmpty() || sessionKeys.isNotEmpty()
        }

    override fun initSnapshot(snapshot: Map<String, List<RepeatEvent>>) {
        synchronized(lock) {
            this.snapshot.clear()
            this.restedUntil.clear()
            if (!pendingClear) {
                snapshot.forEach { (key, events) ->
                    this.snapshot.getOrPut(key) { mutableListOf() }.addAll(events)
                    events.forEach { event ->
                        val cKey = event.canonicalKey
                        if (!cKey.isNullOrBlank() && cKey != key) {
                            this.snapshot.getOrPut(cKey) { mutableListOf() }.add(event)
                        }
                    }
                    val activeRest = events.mapNotNull { it.restUntil }.maxOrNull()
                    if (activeRest != null) {
                        this.restedUntil[key] = activeRest
                        events.mapNotNull { it.canonicalKey }.forEach { cKey ->
                            this.restedUntil[cKey] = activeRest
                        }
                    }
                }
            }
            pendingClear = false
            isReady = true
        }
    }

    override fun clearAll() {
        synchronized(lock) {
            snapshot.clear()
            restedUntil.clear()
            recentAutoPlays.clear()
            sessionKeys.clear()
            lastPlaybackTimeMs = 0L
            if (!isReady) {
                pendingClear = true
            }
        }
    }

    override fun preloadRecentPlays(keys: List<String>) {
        synchronized(lock) {
            for (k in keys) {
                if (k.isNotBlank()) {
                    sessionKeys.add(k)
                    pushRecentPlay(k)
                }
            }
        }
    }

    override fun restItem(key: String, restUntil: Long) {
        if (key.isBlank()) return
        synchronized(lock) {
            this.restedUntil[key] = restUntil
        }
    }

    fun restItem(key: String, canonicalKey: String?, restUntil: Long) {
        if (key.isBlank()) return
        synchronized(lock) {
            this.restedUntil[key] = restUntil
            if (!canonicalKey.isNullOrBlank()) {
                this.restedUntil[canonicalKey] = restUntil
            }
        }
    }

    override fun clearRest(key: String) {
        clearRest(key, null)
    }

    override fun clearRest(key: String, canonicalKey: String?) {
        if (key.isBlank()) return
        synchronized(lock) {
            this.restedUntil.remove(key)
            if (!canonicalKey.isNullOrBlank()) {
                this.restedUntil.remove(canonicalKey)
            }
            listOfNotNull(key, canonicalKey).forEach { k ->
                snapshot[k]?.let { list ->
                    list.removeAll { it.weight == 0.0 }
                    for (i in list.indices) {
                        val e = list[i]
                        if (e.restUntil != null) {
                            list[i] = e.copy(restUntil = null)
                        }
                    }
                }
            }
        }
    }

    override fun isRested(key: String, now: Long): Boolean = isRested(key, null, now)

    override fun isRested(key: String, canonicalKey: String?, now: Long): Boolean {
        synchronized(lock) {
            val until1 = restedUntil[key]
            val until2 = canonicalKey?.let { restedUntil[it] }
            val until = when {
                until1 != null && until2 != null -> maxOf(until1, until2)
                until1 != null -> until1
                else -> until2
            } ?: return false

            return if (until > now) {
                true
            } else {
                restedUntil.remove(key)
                if (canonicalKey != null) restedUntil.remove(canonicalKey)
                false
            }
        }
    }

    override fun restUntil(key: String, now: Long): Long? {
        synchronized(lock) {
            val until = restedUntil[key] ?: return null
            return if (until > now) {
                until
            } else {
                restedUntil.remove(key)
                null
            }
        }
    }

    override fun recordEvent(key: String, playedAt: Long, weight: Double) {
        recordEvent(key, null, playedAt, weight)
    }

    override fun recordEvent(key: String, canonicalKey: String?, playedAt: Long, weight: Double) {
        synchronized(lock) {
            val event = RepeatEvent(playedAt, weight, null, canonicalKey)
            snapshot.getOrPut(key) { mutableListOf() }.add(event)
            if (!canonicalKey.isNullOrBlank() && canonicalKey != key) {
                snapshot.getOrPut(canonicalKey) { mutableListOf() }.add(event)
            }
        }
    }

    override fun recordAutoPlay(key: String) {
        recordAutoPlay(key, null)
    }

    override fun recordAutoPlay(key: String, canonicalKey: String?) {
        synchronized(lock) {
            pushRecentPlay(key)
            sessionKeys.add(key)
            if (!canonicalKey.isNullOrBlank() && canonicalKey != key) {
                pushRecentPlay(canonicalKey)
                sessionKeys.add(canonicalKey)
            }
        }
    }

    private fun pushRecentPlay(key: String) {
        if (recentAutoPlays.size >= RepeatConfig.RECENT_BUFFER_CAPACITY) {
            recentAutoPlays.removeFirst()
        }
        recentAutoPlays.addLast(key)
    }

    override fun hasEventInWindow(key: String, windowMs: Long, now: Long): Boolean =
        hasEventInWindow(key, null, windowMs, now)

    override fun hasEventInWindow(key: String, canonicalKey: String?, windowMs: Long, now: Long): Boolean {
        synchronized(lock) {
            val minTime = now - windowMs
            val ev1 = snapshot[key]
            if (ev1?.any { it.playedAt >= minTime && it.weight > 0.0 } == true) return true
            if (!canonicalKey.isNullOrBlank() && canonicalKey != key) {
                val ev2 = snapshot[canonicalKey]
                if (ev2?.any { it.playedAt >= minTime && it.weight > 0.0 } == true) return true
            }
            return false
        }
    }

    private fun checkSessionReset(now: Long) {
        if (lastPlaybackTimeMs > 0L && (now - lastPlaybackTimeMs) > RepeatConfig.SESSION_IDLE_TIMEOUT_MS) {
            sessionKeys.clear()
        }
        lastPlaybackTimeMs = now
    }

    private fun isInSession(key: String, canonicalKey: String?): Boolean {
        if (key in sessionKeys) return true
        if (canonicalKey != null && canonicalKey in sessionKeys) return true
        return false
    }

    private fun isInGap(key: String, canonicalKey: String?, minGap: Int): Boolean {
        if (minGap <= 0 || recentAutoPlays.isEmpty()) return false
        val gap = recentAutoPlays.takeLast(minGap).toSet()
        if (key in gap) return true
        if (canonicalKey != null && canonicalKey in gap) return true
        return false
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

        var fallbackTriggered = false
        val selected = synchronized(lock) {
            checkSessionReset(now)

            // Level 0: Full policy (Session check active, minGap = 10, Window fatigue active, Rotation & Artist penalty active)
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = RepeatConfig.MIN_GAP,
                checkSession = true,
                checkFatigue = noRepeatHours > 0,
                tauHours = noRepeatHours,
                applyRotationPenalty = true,
                applyArtistPenalty = true,
                currentArtist = currentArtist,
                allowScoreReordering = allowScoreReordering,
                isExempt = isExempt,
                now = now,
            )?.let { return@synchronized it }

            // Step 1: Relax rotation penalty and artist penalty (minGap = 5)
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = RepeatConfig.MIN_GAP_RELAXED,
                checkSession = true,
                checkFatigue = noRepeatHours > 0,
                tauHours = noRepeatHours,
                applyRotationPenalty = false,
                applyArtistPenalty = false,
                currentArtist = currentArtist,
                allowScoreReordering = allowScoreReordering,
                isExempt = isExempt,
                now = now,
            )?.let { return@synchronized it }

            // Step 2: Halve window iteratively (48h -> 24h -> 12h -> 6h -> 1h). Session check remains ACTIVE.
            if (noRepeatHours > 0) {
                var halvedTau = noRepeatHours / 2
                while (halvedTau >= 1) {
                    evaluateLadder(
                        candidates = candidates,
                        items = items,
                        minGap = 0,
                        checkSession = true,
                        checkFatigue = true,
                        tauHours = halvedTau,
                        applyRotationPenalty = false,
                        applyArtistPenalty = false,
                        currentArtist = currentArtist,
                        allowScoreReordering = allowScoreReordering,
                        isExempt = isExempt,
                        now = now,
                    )?.let { return@synchronized it }
                    halvedTau /= 2
                }
            }

            // Step 3: Drop window (tauHours = 0, no fatigue check), but session check remains ACTIVE (minGap = 0)
            evaluateLadder(
                candidates = candidates,
                items = items,
                minGap = 0,
                checkSession = true,
                checkFatigue = false,
                tauHours = 0,
                applyRotationPenalty = false,
                applyArtistPenalty = false,
                currentArtist = currentArtist,
                allowScoreReordering = allowScoreReordering,
                isExempt = isExempt,
                now = now,
            )?.let { return@synchronized it }

            // Step 4: Drop session check as well; pick oldest played track among non-rested (or earliest rest expiry if all rested)
            fallbackTriggered = true
            val nonRested = candidates.filterNot { isRested(items[it].repeatKey, items[it].canonicalKey, now) }
            if (nonRested.isNotEmpty()) {
                nonRested.minByOrNull { idx ->
                    val item = items[idx]
                    val events1 = snapshot[item.repeatKey]
                    val events2 = snapshot[item.canonicalKey]
                    val lastPlayed1 = events1?.filter { it.weight > 0.0 }?.maxOfOrNull { it.playedAt } ?: Long.MIN_VALUE
                    val lastPlayed2 = events2?.filter { it.weight > 0.0 }?.maxOfOrNull { it.playedAt } ?: Long.MIN_VALUE
                    maxOf(lastPlayed1, lastPlayed2)
                } ?: nonRested.first()
            } else {
                candidates.minByOrNull { idx ->
                    val item = items[idx]
                    val u1 = restedUntil[item.repeatKey]
                    val u2 = restedUntil[item.canonicalKey]
                    when {
                        u1 != null && u2 != null -> maxOf(u1, u2)
                        u1 != null -> u1
                        else -> u2 ?: Long.MAX_VALUE
                    }
                } ?: candidates.first()
            }
        }

        if (fallbackTriggered) {
            onFallbackNotice?.invoke()
        }
        return selected
    }

    override fun filterEligibleCandidates(
        candidates: List<Int>,
        items: List<PlayableItem>,
        noRepeatHours: Int,
        isExempt: (PlayableItem) -> Boolean,
        now: Long,
        onFallbackNotice: (() -> Unit)?,
    ): List<Int> {
        if (candidates.isEmpty()) return emptyList()
        if (!isReady) return candidates

        var fallbackTriggered = false
        val eligible = synchronized(lock) {
            checkSessionReset(now)

            fun isCandidateEligible(idx: Int, minGap: Int, checkSession: Boolean, checkFatigue: Boolean, tauHours: Int): Boolean {
                val itm = items.getOrNull(idx) ?: return false
                if (isExempt(itm)) return true
                val key = itm.repeatKey
                val canonicalKey = itm.canonicalKey
                if (isRested(key, canonicalKey, now)) return false
                if (checkSession && isInSession(key, canonicalKey)) return false
                if (isInGap(key, canonicalKey, minGap)) return false
                if (checkFatigue && tauHours > 0) {
                    val fatigue = calculateFatigue(key, canonicalKey, now, tauHours)
                    if (fatigue >= RepeatConfig.FATIGUE_THRESHOLD) return false
                }
                return true
            }

            // Level 0: minGap = 10, checkSession = true, checkFatigue = true
            val l0 = candidates.filter { isCandidateEligible(it, RepeatConfig.MIN_GAP, checkSession = true, checkFatigue = noRepeatHours > 0, tauHours = noRepeatHours) }
            if (l0.isNotEmpty()) return@synchronized l0

            // Step 1: minGap = 5, checkSession = true, checkFatigue = true
            val l1 = candidates.filter { isCandidateEligible(it, RepeatConfig.MIN_GAP_RELAXED, checkSession = true, checkFatigue = noRepeatHours > 0, tauHours = noRepeatHours) }
            if (l1.isNotEmpty()) return@synchronized l1

            // Step 2: Halve window iteratively (checkSession = true)
            if (noRepeatHours > 0) {
                var halvedTau = noRepeatHours / 2
                while (halvedTau >= 1) {
                    val l2 = candidates.filter { isCandidateEligible(it, 0, checkSession = true, checkFatigue = true, tauHours = halvedTau) }
                    if (l2.isNotEmpty()) return@synchronized l2
                    halvedTau /= 2
                }
            }

            // Step 3: Drop window (tau = 0, no fatigue check), checkSession = true
            val l3 = candidates.filter { isCandidateEligible(it, 0, checkSession = true, checkFatigue = false, tauHours = 0) }
            if (l3.isNotEmpty()) return@synchronized l3

            // Step 4: Drop session check as well
            fallbackTriggered = true
            val nonRested = candidates.filterNot { isRested(items[it].repeatKey, items[it].canonicalKey, now) }
            if (nonRested.isNotEmpty()) nonRested else candidates
        }

        if (fallbackTriggered) {
            onFallbackNotice?.invoke()
        }
        return eligible
    }

    private fun evaluateLadder(
        candidates: List<Int>,
        items: List<PlayableItem>,
        minGap: Int,
        checkSession: Boolean,
        checkFatigue: Boolean,
        tauHours: Int,
        applyRotationPenalty: Boolean,
        applyArtistPenalty: Boolean,
        currentArtist: String?,
        allowScoreReordering: Boolean,
        isExempt: (PlayableItem) -> Boolean,
        now: Long,
    ): Int? {
        val eligible = mutableListOf<CandidateScored>()

        for (idx in candidates) {
            val item = items.getOrNull(idx) ?: continue
            if (isExempt(item)) {
                return idx // Explicitly queued items (play next) bypass everything
            }

            val key = item.repeatKey
            val canonicalKey = item.canonicalKey
            if (isRested(key, canonicalKey, now)) continue
            if (checkSession && isInSession(key, canonicalKey)) continue
            if (isInGap(key, canonicalKey, minGap)) continue

            val fatigue = if (checkFatigue && tauHours > 0) calculateFatigue(key, canonicalKey, now, tauHours) else 0.0
            if (checkFatigue && fatigue >= RepeatConfig.FATIGUE_THRESHOLD) continue

            if (!allowScoreReordering) {
                // Linear / plain order: first eligible candidate wins immediately
                return idx
            }

            val affinity = calculateAffinity(key, canonicalKey)
            val rotationPenalty = if (applyRotationPenalty) calculateRotationPenalty(key, canonicalKey, now) else 0.0
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
            val score = affinity.coerceIn(0.0, 1.0) - (1.5 * fatigue) - rotationPenalty - artistPenalty + jitter

            eligible.add(CandidateScored(idx, score))
        }

        return eligible.maxByOrNull { it.score }?.index
    }

    internal fun calculateFatigue(key: String, now: Long, tauHours: Int): Double =
        calculateFatigue(key, null, now, tauHours)

    internal fun calculateFatigue(key: String, canonicalKey: String?, now: Long, tauHours: Int): Double {
        if (tauHours <= 0) return 0.0
        val f1 = calculateFatigueSingle(key, now, tauHours)
        val f2 = if (!canonicalKey.isNullOrBlank() && canonicalKey != key) {
            calculateFatigueSingle(canonicalKey, now, tauHours)
        } else 0.0
        return maxOf(f1, f2)
    }

    private fun calculateFatigueSingle(key: String, now: Long, tauHours: Int): Double {
        val events = snapshot[key] ?: return 0.0
        val tauMs = tauHours * 3600 * 1000.0
        var total = 0.0
        for (event in events) {
            val dt = (now - event.playedAt).coerceAtLeast(0L)
            total += event.weight * exp(-dt / tauMs)
        }
        return total
    }

    internal fun calculateRotationPenalty(key: String, canonicalKey: String?, now: Long): Double {
        val r1 = calculateRotationWeightSingle(key, now)
        val r2 = if (!canonicalKey.isNullOrBlank() && canonicalKey != key) {
            calculateRotationWeightSingle(canonicalKey, now)
        } else 0.0
        val totalWeight = maxOf(r1, r2)
        return (totalWeight * 0.15).coerceIn(0.0, 1.5)
    }

    private fun calculateRotationWeightSingle(key: String, now: Long): Double {
        val events = snapshot[key] ?: return 0.0
        val decayMs = RepeatConfig.ROTATION_DECAY_DAYS * 24 * 3600 * 1000.0
        var total = 0.0
        for (event in events) {
            if (event.weight <= 0.0) continue
            val dt = (now - event.playedAt).coerceAtLeast(0L)
            total += event.weight * exp(-dt / decayMs)
        }
        return total
    }

    internal fun calculateAffinity(key: String): Double = calculateAffinity(key, null)

    internal fun calculateAffinity(key: String, canonicalKey: String?): Double {
        val a1 = calculateAffinitySingle(key)
        val a2 = if (!canonicalKey.isNullOrBlank() && canonicalKey != key) {
            calculateAffinitySingle(canonicalKey)
        } else 0.0
        return maxOf(a1, a2)
    }

    private fun calculateAffinitySingle(key: String): Double {
        val events = snapshot[key] ?: return 0.0
        val totalWeight = events.sumOf { it.weight }
        return (totalWeight / 3.0).coerceIn(0.0, 1.0)
    }

    private data class CandidateScored(val index: Int, val score: Double)
}
