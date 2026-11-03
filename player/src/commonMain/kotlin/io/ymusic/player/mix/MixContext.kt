package io.ymusic.player.mix

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

data class SeedProfile(
    val language: Language,
    val mood: Mood,
    val weight: Double,
)

class MixContext {
    private val lock = SynchronizedObject()

    // Up to 5 manual choices with decaying weights
    private val manualSeeds = ArrayDeque<Pair<Language, Mood>>(5)

    // Last 10 auto-picks: true = target language, false = discovery
    private val autoPickWindow = ArrayDeque<Boolean>(10)

    // Penalized (Language, Mood) combos from quick skips (<10s) in current session
    private val quickSkipPenalties = mutableSetOf<Pair<Language, Mood>>()

    fun recordManualPlay(language: Language, mood: Mood) {
        synchronized(lock) {
            if (manualSeeds.size >= 5) {
                manualSeeds.removeFirst()
            }
            manualSeeds.addLast(language to mood)
        }
    }

    fun recordAutoPick(isTargetLanguage: Boolean) {
        synchronized(lock) {
            if (autoPickWindow.size >= 10) {
                autoPickWindow.removeFirst()
            }
            autoPickWindow.addLast(isTargetLanguage)
        }
    }

    fun recordQuickSkip(language: Language, mood: Mood) {
        synchronized(lock) {
            quickSkipPenalties.add(language to mood)
        }
    }

    fun isPenalized(language: Language, mood: Mood): Boolean =
        synchronized(lock) {
            quickSkipPenalties.contains(language to mood)
        }

    fun getTargetLanguage(): Language {
        return synchronized(lock) {
            if (manualSeeds.isEmpty()) return@synchronized Language.UNKNOWN

            // Decay weights: newest item gets 1.0, older items get proportionally less
            val langWeights = mutableMapOf<Language, Double>()
            val n = manualSeeds.size
            manualSeeds.forEachIndexed { idx, (lang, _) ->
                if (lang != Language.UNKNOWN) {
                    val weight = 0.5 + (0.5 * (idx + 1).toDouble() / n)
                    langWeights[lang] = (langWeights[lang] ?: 0.0) + weight
                }
            }

            langWeights.maxByOrNull { it.value }?.key ?: manualSeeds.last().first
        }
    }

    fun getTargetMood(): Mood {
        return synchronized(lock) {
            if (manualSeeds.isEmpty()) return@synchronized Mood.UNKNOWN

            // Most recent manual seed's mood, or weighted mode if known
            val moodWeights = mutableMapOf<Mood, Double>()
            val n = manualSeeds.size
            manualSeeds.forEachIndexed { idx, (_, mood) ->
                if (mood != Mood.UNKNOWN) {
                    val weight = 0.5 + (0.5 * (idx + 1).toDouble() / n)
                    moodWeights[mood] = (moodWeights[mood] ?: 0.0) + weight
                }
            }

            moodWeights.maxByOrNull { it.value }?.key ?: manualSeeds.last().second
        }
    }

    /**
     * True if the sliding window is in target deficit (<80% target picks).
     * When in deficit, target picks must take absolute priority.
     */
    fun isTargetDeficit(): Boolean {
        return synchronized(lock) {
            val discoveryCount = autoPickWindow.count { !it }
            if (discoveryCount >= 2) return@synchronized true
            if (autoPickWindow.isNotEmpty() && !autoPickWindow.last()) return@synchronized true
            if (autoPickWindow.size < 10 && discoveryCount >= 1) return@synchronized true
            false
        }
    }

    fun getWindowStats(): String {
        return synchronized(lock) {
            val targets = autoPickWindow.count { it }
            val size = autoPickWindow.size
            val pct = if (size > 0) (targets * 100 / size) else 100
            "$targets/$size ($pct%)"
        }
    }

    fun clear() {
        synchronized(lock) {
            manualSeeds.clear()
            autoPickWindow.clear()
            quickSkipPenalties.clear()
        }
    }
}
