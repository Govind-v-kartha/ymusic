package io.ymusic.settings

object PlatformDefaults {
    /**
     * Default value for [AppSettings.resumeOnLaunch].
     * Defaults to true (standard for mobile playback resumption).
     * Desktop shells configure this to false at startup before reading settings.
     */
    var resumeOnLaunchDefault: Boolean = true
}
