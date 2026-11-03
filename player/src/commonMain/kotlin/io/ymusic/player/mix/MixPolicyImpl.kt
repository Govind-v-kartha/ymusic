package io.ymusic.player.mix

import io.ymusic.player.PlayableItem
import kmpcommon.log.Logger

class MixPolicyImpl(
    val artistLanguageStore: LearnedArtistLanguageStore = LearnedArtistLanguageStore(),
    val context: MixContext = MixContext(),
    private val songMixEnabled: () -> Boolean = { true },
    private val keepMoodEnabled: () -> Boolean = { true },
    var logger: Logger? = null,
) : MixPolicy {

    override val isEnabled: Boolean
        get() = songMixEnabled()

    override val keepMood: Boolean
        get() = keepMoodEnabled()

    override fun onManualPlay(item: PlayableItem) {
        val lang = LanguageClassifier.classify(item.displayTitle, item.artistName, artistLanguageStore)
        val mood = MoodClassifier.classify(item.displayTitle, item.artistName)

        // Only record observation for artist if direct script/keywords were unambiguous
        val scriptLang = LanguageClassifier.detectScript(listOfNotNull(item.displayTitle, item.artistName).joinToString(" "))
        if (scriptLang != null) {
            artistLanguageStore.recordObservation(item.artistName, scriptLang)
        }

        context.recordManualPlay(lang, mood)
    }

    override fun onAutoPick(item: PlayableItem) {
        val targetLang = context.getTargetLanguage()
        val lang = LanguageClassifier.classify(item.displayTitle, item.artistName, artistLanguageStore)
        val mood = MoodClassifier.classify(item.displayTitle, item.artistName)

        val scriptLang = LanguageClassifier.detectScript(listOfNotNull(item.displayTitle, item.artistName).joinToString(" "))
        if (scriptLang != null) {
            artistLanguageStore.recordObservation(item.artistName, scriptLang)
        }

        val isTarget = (targetLang == Language.UNKNOWN) || (lang == targetLang)
        context.recordAutoPick(isTarget)
    }

    override fun onQuickSkip(item: PlayableItem, playDurationMs: Long) {
        val lang = LanguageClassifier.classify(item.displayTitle, item.artistName, artistLanguageStore)
        val mood = MoodClassifier.classify(item.displayTitle, item.artistName)
        context.recordQuickSkip(lang, mood)
    }

    override fun selectNextCandidate(
        candidates: List<Int>,
        items: List<PlayableItem>,
        currentIndex: Int,
    ): MixSelectionResult {
        if (candidates.isEmpty()) {
            return MixSelectionResult(null, 0, false, "empty")
        }

        val targetLang = context.getTargetLanguage()
        val targetMood = context.getTargetMood()
        val enforceMood = keepMood && targetMood != Mood.UNKNOWN
        val inDeficit = context.isTargetDeficit()

        data class CandidateInfo(
            val index: Int,
            val item: PlayableItem,
            val language: Language,
            val mood: Mood,
            val isPenalized: Boolean,
        )

        val pool = candidates.map { idx ->
            val itm = items[idx]
            val lang = LanguageClassifier.classify(itm.displayTitle, itm.artistName, artistLanguageStore)
            val md = MoodClassifier.classify(itm.displayTitle, itm.artistName)
            val penalized = context.isPenalized(lang, md)
            CandidateInfo(idx, itm, lang, md, penalized)
        }

        val targetCount = if (targetLang != Language.UNKNOWN) {
            pool.count { it.language == targetLang }
        } else {
            pool.size
        }

        val shouldRefill = targetCount < 3

        var pickedInfo: CandidateInfo? = null
        var relaxedStep = "Target+Mood"

        fun evaluateLadderOn(candidatePool: List<CandidateInfo>): CandidateInfo? {
            // Ladder Step 1: Target + Mood
            if (enforceMood && targetLang != Language.UNKNOWN) {
                candidatePool.firstOrNull { it.language == targetLang && it.mood == targetMood }?.let {
                    relaxedStep = "Target+Mood"
                    return it
                }
            }

            // Ladder Step 2: Target + Any Mood
            if (targetLang != Language.UNKNOWN) {
                candidatePool.firstOrNull { it.language == targetLang }?.let {
                    relaxedStep = "Target+Any"
                    return it
                }
            }

            // Ladder Step 3: Neutral/Unknown/English + Mood
            if (enforceMood) {
                candidatePool.firstOrNull {
                    (it.language == Language.UNKNOWN || it.language == Language.ENGLISH) && it.mood == targetMood
                }?.let {
                    relaxedStep = "Neutral+Mood"
                    return it
                }
            }

            // Ladder Step 4: Any Language + Mood
            if (enforceMood) {
                candidatePool.firstOrNull { it.mood == targetMood }?.let {
                    relaxedStep = "Any+Mood"
                    return it
                }
            }

            // Ladder Step 5: All (Fallback)
            candidatePool.firstOrNull()?.let {
                relaxedStep = "All"
                return it
            }

            return null
        }

        // If not in deficit and we have a rich target pool, allow 20% discovery slot pick
        if (!inDeficit && targetCount >= 3) {
            val discoveryList = pool.filter { it.language != targetLang }
            if (discoveryList.isNotEmpty()) {
                val nonPenalizedDiscovery = discoveryList.filter { !it.isPenalized }
                val activeDiscovery = nonPenalizedDiscovery.ifEmpty { discoveryList }
                val discoveryMood = if (enforceMood) activeDiscovery.filter { it.mood == targetMood } else activeDiscovery
                val candidate = discoveryMood.firstOrNull() ?: activeDiscovery.firstOrNull()
                if (candidate != null) {
                    pickedInfo = candidate
                    relaxedStep = "Discovery"
                }
            }
        }

        if (pickedInfo == null) {
            // First evaluate ladder prioritizing non-penalized items
            val nonPenalizedPool = pool.filter { !it.isPenalized }
            if (nonPenalizedPool.isNotEmpty()) {
                pickedInfo = evaluateLadderOn(nonPenalizedPool)
            }
            if (pickedInfo == null) {
                // Fall back to entire pool if all remaining are penalized
                pickedInfo = evaluateLadderOn(pool)
            }
        }

        val selected = pickedInfo ?: pool.first()
        val stats = context.getWindowStats()
        val logLine = "[mix] picked '${selected.item.displayTitle}' lang=${selected.language} mood=${selected.mood} window=$stats target=80% relaxed=$relaxedStep"

        logger?.fork("mix")?.log(Logger.Level.INFO, logLine)

        return MixSelectionResult(
            selectedIndex = selected.index,
            targetCandidateCount = targetCount,
            shouldRefill = shouldRefill,
            debugInfo = logLine,
        )
    }

    override fun clearHistory() {
        context.clear()
        artistLanguageStore.clear()
    }
}
