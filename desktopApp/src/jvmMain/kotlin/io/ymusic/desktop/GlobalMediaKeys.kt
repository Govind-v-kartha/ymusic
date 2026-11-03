package io.ymusic.desktop

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.ymusic.player.Player
import kmpcommon.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OS-level global hotkeys that intercept physical media keys even when the app is
 * unfocused or minimized to the system tray.
 */
internal class GlobalMediaKeys(
    private val playerProvider: () -> Player?,
    private val onToggleWindow: () -> Unit,
    private val scope: CoroutineScope,
    private val log: Logger? = null,
) {
    private val active = AtomicBoolean(true)
    private var listenerThread: Thread? = null

    init {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        when {
            "win" in os -> startWindowsListener()
            "linux" in os -> startLinuxListener()
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

                // Virtual key codes for media transport
                val vkMediaNext = 0xB0
                val vkMediaPrev = 0xB1
                val vkMediaStop = 0xB2
                val vkMediaPlayPause = 0xB3

                // MOD_NOREPEAT = 0x4000
                val noRepeat = 0x4000
                registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PLAY_PAUSE, noRepeat, vkMediaPlayPause))
                registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_NEXT, noRepeat, vkMediaNext))
                registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PREV, noRepeat, vkMediaPrev))
                registerHotKey.invokeInt(arrayOf(null, HOTKEY_ID_STOP, noRepeat, vkMediaStop))

                // Structure for MSG: HWND, UINT, WPARAM, LPARAM, DWORD, POINT (48 bytes on x64)
                val msg = com.sun.jna.Memory(48)
                while (active.get()) {
                    val res = getMessage.invokeInt(arrayOf(msg, null, 0, 0))
                    if (res <= 0) break

                    // message == WM_HOTKEY (0x0312)
                    val messageId = msg.getInt(NativeLongHelper.OFFSET_MESSAGE)
                    if (messageId == 0x0312) {
                        val id = msg.getInt(NativeLongHelper.OFFSET_WPARAM)
                        scope.launch(Dispatchers.Main) {
                            val player = playerProvider() ?: return@launch
                            when (id) {
                                HOTKEY_ID_PLAY_PAUSE -> player.togglePlay()
                                HOTKEY_ID_NEXT -> player.skipNext()
                                HOTKEY_ID_PREV -> player.skipPrev()
                                HOTKEY_ID_STOP -> player.pause()
                                HOTKEY_ID_TOGGLE_WINDOW -> onToggleWindow()
                            }
                        }
                    }
                }

                unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PLAY_PAUSE))
                unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_NEXT))
                unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_PREV))
                unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_STOP))
                unregisterHotKey.invokeInt(arrayOf(null, HOTKEY_ID_TOGGLE_WINDOW))
            } catch (e: Throwable) {
                log?.log(Logger.Level.DEBUG, "Windows global hotkeys error", e)
            }
        }, "win-global-hotkeys").apply {
            isDaemon = true
            start()
        }
    }

    private fun startLinuxListener() {
        val x11 = runCatching { NativeLibrary.getInstance("X11") }.getOrNull() ?: return

        listenerThread = Thread({
            try {
                val openDisplay = x11.getFunction("XOpenDisplay")
                val display = openDisplay.invokePointer(arrayOf(null)) ?: return@Thread
                val defaultRootWindow = x11.getFunction("XDefaultRootWindow")
                val rootWindow = defaultRootWindow.invokePointer(arrayOf(display)) ?: return@Thread

                val stringToKeysym = x11.getFunction("XStringToKeysym")
                val keysymToKeycode = x11.getFunction("XKeysymToKeycode")
                val grabKey = x11.getFunction("XGrabKey")
                val nextEvent = x11.getFunction("XNextEvent")
                val closeDisplay = x11.getFunction("XCloseDisplay")

                val mediaKeys = listOf("XF86AudioPlay", "XF86AudioPause", "XF86AudioNext", "XF86AudioPrev", "XF86AudioStop")
                val keycodes = mediaKeys.mapNotNull { name ->
                    val keysym = stringToKeysym.invokeLong(arrayOf(name))
                    if (keysym != 0L) {
                        val keycode = keysymToKeycode.invokeInt(arrayOf(display, keysym))
                        if (keycode != 0) keycode to name else null
                    } else null
                }.toMap()

                for ((kc, _) in keycodes) {
                    // AnyModifier = 0x8000
                    grabKey.invokeInt(arrayOf(display, kc, 0x8000, rootWindow, 1, 1, 1))
                }

                val xEvent = com.sun.jna.Memory(192)
                while (active.get()) {
                    nextEvent.invokeVoid(arrayOf(display, xEvent))
                    // type == KeyPress (2)
                    val type = xEvent.getInt(0)
                    if (type == 2) {
                        // keycode is at offset (typically 84 on 64-bit XKeyEvent)
                        val kc = xEvent.getInt(NativeLongHelper.OFFSET_XKEYEVENT_KEYCODE)
                        val name = keycodes[kc]
                        if (name != null) {
                            scope.launch(Dispatchers.Main) {
                                val player = playerProvider() ?: return@launch
                                when (name) {
                                    "XF86AudioPlay", "XF86AudioPause" -> player.togglePlay()
                                    "XF86AudioNext" -> player.skipNext()
                                    "XF86AudioPrev" -> player.skipPrev()
                                    "XF86AudioStop" -> player.pause()
                                }
                            }
                        }
                    }
                }

                closeDisplay.invokeInt(arrayOf(display))
            } catch (e: Throwable) {
                log?.log(Logger.Level.DEBUG, "Linux X11 global hotkeys error", e)
            }
        }, "linux-global-hotkeys").apply {
            isDaemon = true
            start()
        }
    }

    private object NativeLongHelper {
        val is64Bit = com.sun.jna.Native.POINTER_SIZE == 8
        val OFFSET_MESSAGE: Long = if (is64Bit) 8L else 4L
        val OFFSET_WPARAM: Long = if (is64Bit) 16L else 8L
        val OFFSET_XKEYEVENT_KEYCODE: Long = if (is64Bit) 84L else 52L
    }

    companion object {
        private const val HOTKEY_ID_PLAY_PAUSE = 1001
        private const val HOTKEY_ID_NEXT = 1002
        private const val HOTKEY_ID_PREV = 1003
        private const val HOTKEY_ID_STOP = 1004
        private const val HOTKEY_ID_TOGGLE_WINDOW = 1005
    }
}

