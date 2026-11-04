package io.ymusic.desktop

import kmpcommon.log.Logger
import java.io.File

/**
 * Crash guard for JNA-based native integrations (Windows SMTC, Linux MPRIS, Global Hotkeys).
 * Protects against JVM hard crashes caused by segfaults in native libraries.
 */
internal class NativeIntegrationGuard(
    private val dataDir: File,
    private val log: Logger? = null,
    private val getEnv: (String) -> String? = { System.getenv(it) },
) {
    private val markerFile = File(dataDir, ".native_crash_marker")

    private val disabledByPriorCrash: Boolean by lazy {
        if (markerFile.exists()) {
            log?.log(
                Logger.Level.WARN,
                "Native integrations disabled: previous run crashed during native initialization (marker present)",
            )
            // Remove the marker so the NEXT launch tries again
            markerFile.delete()
            true
        } else {
            false
        }
    }

    /**
     * Determines whether native integrations are allowed to initialize.
     * Returns false if explicitly disabled via environment variable or if a prior crash occurred.
     */
    fun shouldInitialize(): Boolean {
        if (getEnv("YMUSIC_DISABLE_NATIVE_INTEGRATIONS") == "1") {
            log?.log(Logger.Level.WARN, "Native integrations disabled via YMUSIC_DISABLE_NATIVE_INTEGRATIONS=1")
            return false
        }
        if (disabledByPriorCrash) {
            return false
        }
        return true
    }

    /**
     * Wraps native initialization with a crash marker.
     * Writes the marker before executing [block], and removes it immediately upon success.
     */
    inline fun <T> runGuarded(block: () -> T): T? {
        if (!shouldInitialize()) return null
        markStarting()
        return try {
            val result = block()
            markFinished()
            result
        } catch (t: Throwable) {
            markFinished()
            log?.log(Logger.Level.WARN, "Native initialization threw managed exception", t)
            null
        }
    }

    fun markStarting() {
        runCatching {
            dataDir.mkdirs()
            markerFile.writeText("initializing\n")
        }
    }

    fun markFinished() {
        runCatching {
            if (markerFile.exists()) {
                markerFile.delete()
            }
        }
    }

    fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                markFinished()
            },
        )
    }
}
