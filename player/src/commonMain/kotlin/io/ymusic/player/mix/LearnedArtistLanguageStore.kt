package io.ymusic.player.mix

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class LearnedArtistLanguageStore {
    private val lock = SynchronizedObject()
    private val channelObservations = mutableMapOf<String, MutableMap<Language, Int>>()

    fun recordObservation(artistOrChannel: String?, language: Language) {
        if (artistOrChannel.isNullOrBlank()) return
        if (language == Language.UNKNOWN || language == Language.OTHER) return
        val key = normalizeKey(artistOrChannel)
        if (key.isBlank()) return
        synchronized(lock) {
            val map = channelObservations.getOrPut(key) { mutableMapOf() }
            map[language] = (map[language] ?: 0) + 1
        }
    }

    fun getDecisiveLanguage(artistOrChannel: String?): Language? {
        if (artistOrChannel.isNullOrBlank()) return null
        val key = normalizeKey(artistOrChannel)
        return synchronized(lock) {
            val map = channelObservations[key] ?: return@synchronized null
            val total = map.values.sum()
            if (total < 3) return@synchronized null
            val topEntry = map.maxByOrNull { it.value } ?: return@synchronized null
            if ((topEntry.value.toDouble() / total) >= 0.70) {
                topEntry.key
            } else {
                null
            }
        }
    }

    fun getObservations(artistOrChannel: String?): Map<Language, Int> {
        if (artistOrChannel.isNullOrBlank()) return emptyMap()
        val key = normalizeKey(artistOrChannel)
        return synchronized(lock) {
            channelObservations[key]?.toMap() ?: emptyMap()
        }
    }

    fun clear() {
        synchronized(lock) {
            channelObservations.clear()
        }
    }

    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase()
}
