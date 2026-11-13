package io.ymusic.player.repeat

object RepeatConfig {
    const val MIN_GAP = 10
    const val MIN_GAP_RELAXED = 5
    const val RECENT_BUFFER_CAPACITY = 50
    const val FATIGUE_THRESHOLD = 1.0
    const val WEIGHT_QUALIFYING = 1.0
    const val WEIGHT_REWIND = 0.5
    const val WEIGHT_REPLAY_OR_SEARCH = 1.0
    const val JITTER_MAX = 0.1
    const val SAME_ARTIST_PENALTY = 0.5
    const val HISTORY_WINDOW_MS = 35 * 24 * 60 * 60 * 1000L
    const val SESSION_IDLE_TIMEOUT_MS = 3 * 60 * 60 * 1000L
    const val ROTATION_DECAY_DAYS = 30
}
