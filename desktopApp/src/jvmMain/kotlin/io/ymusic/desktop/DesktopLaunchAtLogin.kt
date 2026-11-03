package io.ymusic.desktop

import java.io.File

/**
 * Handles toggling autostart ("Launch at login") for Windows and Linux.
 */
internal object DesktopLaunchAtLogin {
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")

    fun isEnabled(): Boolean {
        return try {
            if (isWindows) {
                val proc = ProcessBuilder(
                    "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "YMusic"
                ).redirectErrorStream(true).start()
                proc.waitFor() == 0
            } else if (isLinux) {
                val autostartFile = File(System.getProperty("user.home"), ".config/autostart/ymusic.desktop")
                autostartFile.exists()
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun setEnabled(enabled: Boolean, exePath: String? = null) {
        try {
            if (isWindows) {
                if (enabled) {
                    val exe = exePath ?: currentExePath() ?: return
                    ProcessBuilder(
                        "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", "YMusic", "/t", "REG_SZ", "/d", "\"$exe\"", "/f"
                    ).start().waitFor()
                } else {
                    ProcessBuilder(
                        "reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", "YMusic", "/f"
                    ).start().waitFor()
                }
            } else if (isLinux) {
                val autostartDir = File(System.getProperty("user.home"), ".config/autostart")
                val autostartFile = File(autostartDir, "ymusic.desktop")
                if (enabled) {
                    autostartDir.mkdirs()
                    val exe = exePath ?: "/opt/ymusic/bin/ymusic"
                    val content = """
                        |[Desktop Entry]
                        |Type=Application
                        |Name=YMusic
                        |Exec=$exe
                        |Icon=ymusic
                        |Terminal=false
                        |Categories=Audio;Music;
                    """.trimMargin()
                    autostartFile.writeText(content)
                } else {
                    if (autostartFile.exists()) {
                        autostartFile.delete()
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun currentExePath(): String? {
        val processHandle = ProcessHandle.current()
        val info = processHandle.info()
        return info.command().orElse(null)
    }
}

