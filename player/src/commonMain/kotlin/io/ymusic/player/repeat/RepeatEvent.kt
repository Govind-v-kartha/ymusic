package io.ymusic.player.repeat

data class RepeatEvent(
    val playedAt: Long,
    val weight: Double,
    val restUntil: Long? = null,
)
