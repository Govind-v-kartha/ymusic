package io.ymusic.player.mix

import io.ymusic.player.PlayableItem

data class MixSelectionResult(
    val selectedIndex: Int?,
    val targetCandidateCount: Int,
    val shouldRefill: Boolean,
    val debugInfo: String,
)

interface MixPolicy {
    val isEnabled: Boolean
    val keepMood: Boolean

    fun onManualPlay(item: PlayableItem)
    fun onAutoPick(item: PlayableItem)
    fun onQuickSkip(item: PlayableItem, playDurationMs: Long)

    fun selectNextCandidate(
        candidates: List<Int>,
        items: List<PlayableItem>,
        currentIndex: Int,
    ): MixSelectionResult

    fun clearHistory()
}
