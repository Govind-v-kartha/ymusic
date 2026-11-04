package io.ymusic.desktop

import com.sun.jna.NativeLibrary
import io.ymusic.player.Player
import kmpcommon.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OS-level global hotkeys for desktop platforms.
 *
 * Platform policy:
 * - Linux: Media keys are handled natively by MPRIS2 over D-Bus; XGrabKey is omitted because modern
 *   distributions (such as Ubuntu 22.04+) default to Wayland where global X11 grabs are ineffective.
 * - Windows: Media keys (VK_MEDIA_*) are registered ONLY when SMTC fails to initialize (or is disabled),
 *   preventing any double-handling of play/pause/prev/next presses.
 * - Show/Hide Hotkey: Handled separately on Windows only, defaulting to none, configurable in Settings.
 */
internal class GlobalMediaKeys(
    private val playerProvider: () -> Player?,
    private val onToggleWindow: () -> Unit,
    private val scope: CoroutineScope,
    private val log: Logger? = null,
    private val smtcActive: Boolean = false,
    private val showHideHotkey: String? = null,
) {
    private val active = AtomicBoolean(true)
    private var listenerThread: Thread? = null

    init {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if ("win" in os) {
            startWindowsListener()
        }
    }

    fun release() {
        active.set(false)
        listenerThread?.interrupt()
        listenerThread = null
    }

    private fun startWindowsListener() {
        val user32 = runCatching { NativeLibrary.getInstance("user32") }.getOrNull() ?: return

        listenerThread = Thread({
            try {
                val registerHotKey = user32.getFunction("RegisterHotKey")
                val unregisterHotKey = user32.getFunction("UnregisterHotKey")
                val getMessage = user32.getFunction("GetMessageW")

                // MOD_NOREPEAT = 0x4000
                val noRepeat = 0x4000

                // Register VK_MEDIA_* ONLY if SMTC is not active to prevent double-handling
                if (!smtcActive) {
                    val vkMediaNext = 0xB0
                    val vkMediaPrev = 0xB1
                    val vkMediaStop = 0xB2
                    val vkMediaPlayPause = 0xB3

                    registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PLAY_PAUSE, noRepeat, vkMediaPlayPause))
                    registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_NEXT, noRepeat, vkMediaNext))
                    registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PREV, noRepeat, vkMediaPrev))
                    registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_STOP, noRepeat, vkMediaStop))
                    log?.log(Logger.Level.DEBUG, "Registered Windows fallback media hotkeys (SMTC inactive)")
                } else {
                    log?.log(Logger.Level.DEBUG, "SMTC is active; skipping VK_MEDIA_* registration to prevent duplicate events")
                }

                // Register optional Show/Hide hotkey if configured (e.g. MOD_CONTROL | MOD_ALT = 0x0003, 'Y' = 0x59)
                val hasToggleHotkey = !showHideHotkey.isNullOrBlank()
                if (hasToggleHotkey) {
                    // Default / configured toggle hotkey: Ctrl + Alt + Y (0x59)
                    registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_TOGGLE_WINDOW, noRepeat or 0x0003, 0x59))
                }

                // MSG: HWND, UINT, WPARAM, LPARAM, DWORD, POINT (48 bytes on x64)
                val msg = com.sun.jna.Memory(48)
                while (active.get()) {
                    val res = getMessage.invokeInt(arrayOf(msg, null, 0, 0))
                    if (res <= 0) break

                    // message == WM_HOTKEY (0x0312)
                    val messageId = msg.getInt(NativeLongHelper.OFFSET_MESSAGE)
                    if (messageId == 0x0312) {
                        val id = msg.getInt(NativeLongHelper.OFFSET_WPARAM)
                        scope.launch(Dispatchers.Main) {
                            val player = playerProvider()
                            when (id) {
                                HOTKEY_ID_PLAY_PAUSE -> player?.togglePlay()
                                HOTKEY_ID_NEXT -> player?.skipNext()
                                HOTKEY_ID_PREV -> player?.skipPrev()
                                HOTKEY_ID_STOP -> player?.pause()
                                HOTKEY_ID_TOGGLE_WINDOW -> onToggleWindow()
                            }
                        }
                    }
                }

                if (!smtcActive) {
                    unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PLAY_PAUSE))
                    unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_NEXT))
                    unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PREV))
                    unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_STOP))
                }
                if (hasToggleHotkey) {
                    unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_TOGGLE_WINDOW))
                }
            } catch (e: Throwable) {
                log?.log(Logger.Level.DEBUG, "Windows global hotkeys error", e)
            }
        }, "win-global-hotkeys").apply {
            isDaemon = true
            start()
        }
    }

    private object NativeLongHelper {
        val is64Bit = com.sun.jna.Native.POINTER_SIZE == 8
        val OFFSET_MESSAGE: Long = if (is64Bit) 8L else 4L
        val OFFSET_WPARAM: Long = if (is64Bit) 16L else 8L
    }

    companion object {
        private const val HOTKEY_ID_PLAY_PAUSE = 1001
        private const val HOTKEY_ID_NEXT = 1002
        private const val HOTKEY_ID_PREV = 1003
        private const val HOTKEY_ID_STOP = 1004
        private const val HOTKEY_ID_TOGGLE_WINDOW = 1005
    }
}
