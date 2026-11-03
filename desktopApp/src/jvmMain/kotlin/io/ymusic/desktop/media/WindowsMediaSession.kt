package io.ymusic.desktop.media

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import io.ymusic.desktop.WindowsCom
import io.ymusic.desktop.checkHresult
import io.ymusic.desktop.comCall
import io.ymusic.desktop.comRelease
import kmpcommon.log.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Windows 10/11 SystemMediaTransportControls (SMTC) media session implementation using JNA.
 * Hooks into the OS media overlay and Quick Settings / taskbar media controls.
 */
internal class WindowsMediaSession(
    private val commands: MediaSessionCommands,
    private val log: Logger? = null,
) : DesktopMediaSession {

    private val combase: NativeLibrary? = runCatching { NativeLibrary.getInstance("combase") }.getOrNull()
    private val user32: NativeLibrary? = runCatching { NativeLibrary.getInstance("user32") }.getOrNull()

    private var smtc: Pointer? = null
    private var displayUpdater: Pointer? = null
    private var musicProperties: Pointer? = null
    private var buttonToken: Long = 0L
    private val released = AtomicBoolean(false)

    // Keep callback reference strongly reachable
    private val buttonPressedCallback = object : Callback {
        fun invoke(thisPtr: Pointer?, sender: Pointer?, args: Pointer?): Int {
            if (args != null) {
                // ISystemMediaTransportControlsButtonPressedEventArgs
                // vtable 6: get_Button(SystemMediaTransportControlsButton* value)
                val buttonRef = Memory(4)
                val hr = args.comCall(6, buttonRef)
                if (hr >= 0) {
                    when (buttonRef.getInt(0)) {
                        BUTTON_PLAY -> commands.play()
                        BUTTON_PAUSE -> commands.pause()
                        BUTTON_STOP -> commands.pause()
                        BUTTON_RECORD -> {}
                        BUTTON_FAST_FORWARD -> {}
                        BUTTON_REWIND -> {}
                        BUTTON_NEXT -> commands.next()
                        BUTTON_PREVIOUS -> commands.previous()
                        BUTTON_CHANNEL_UP -> {}
                        BUTTON_CHANNEL_DOWN -> {}
                    }
                }
            }
            return 0 // S_OK
        }
    }

    init {
        try {
            initSmtc()
        } catch (e: Throwable) {
            log?.log(Logger.Level.WARN, "Windows SMTC init failed, falling back to background transport", e)
            release()
        }
    }

    private fun initSmtc() {
        if (combase == null || user32 == null) return

        // Initialize WinRT single-threaded or multithreaded apartment
        val roInit = combase.getFunction("RoInitialize")
        roInit.invokeInt(arrayOf(1)) // RO_INIT_MULTITHREADED

        // Find or create window handle for SMTC interop
        val getDesktopWindow = user32.getFunction("GetDesktopWindow")
        val hwnd: Pointer = getDesktopWindow.invokePointer(arrayOf())

        // Class: "Windows.Media.SystemMediaTransportControls"
        val hClassName = createHString("Windows.Media.SystemMediaTransportControls")
        val interopIid = WindowsCom.guid(IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS_INTEROP)
        val factoryRef = PointerByReference()

        val roGetActivationFactory = combase.getFunction("RoGetActivationFactory")
        val hrFactory = roGetActivationFactory.invokeInt(arrayOf(hClassName, interopIid, factoryRef))
        deleteHString(hClassName)
        hrFactory.checkHresult("RoGetActivationFactory(SystemMediaTransportControls)")

        val interop = factoryRef.value
        val smtcIid = WindowsCom.guid(IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS)
        val smtcRef = PointerByReference()

        // ISystemMediaTransportControlsInterop::GetForWindow(HWND appWindow, REFIID riid, void** mediaTransportControl)
        // vtable slot 3
        val hrGet = interop.comCall(3, hwnd, smtcIid, smtcRef)
        interop.comRelease()
        hrGet.checkHresult("ISystemMediaTransportControlsInterop::GetForWindow")

        val s = smtcRef.value
        smtc = s

        // Enable buttons
        s.comCall(7, 1) // put_IsEnabled(true)
        s.comCall(9, 1) // put_IsPlayEnabled(true)
        s.comCall(10, 1) // put_IsPauseEnabled(true)
        s.comCall(11, 1) // put_IsStopEnabled(true)
        s.comCall(14, 1) // put_IsNextEnabled(true)
        s.comCall(15, 1) // put_IsPreviousEnabled(true)

        // Get DisplayUpdater (vtable slot 16)
        val updaterRef = PointerByReference()
        if (s.comCall(16, updaterRef) >= 0 && updaterRef.value != null) {
            val updater = updaterRef.value
            displayUpdater = updater
            // put_Type(MediaPlaybackType_Music = 1) (vtable slot 7)
            updater.comCall(7, 1)

            // get_MusicProperties (vtable slot 8)
            val musicRef = PointerByReference()
            if (updater.comCall(8, musicRef) >= 0) {
                musicProperties = musicRef.value
            }
        }
    }

    override fun update(state: MediaSessionState) {
        val s = smtc ?: return
        if (released.get()) return

        try {
            // PlaybackStatus: Closed = 0, Changing = 1, Stopped = 2, Playing = 3, Paused = 4
            val status = when {
                !state.hasItem -> 2 // Stopped
                state.playing -> 3 // Playing
                else -> 4 // Paused
            }
            s.comCall(6, status) // put_PlaybackStatus

            val props = musicProperties
            val updater = displayUpdater
            if (props != null && updater != null) {
                val titleStr = createHString(state.title ?: "")
                props.comCall(7, titleStr) // put_Title
                deleteHString(titleStr)

                val artistStr = createHString(state.artist ?: "")
                props.comCall(9, artistStr) // put_Artist
                deleteHString(artistStr)

                updater.comCall(11) // Update()
            }
        } catch (e: Throwable) {
            log?.log(Logger.Level.DEBUG, "Failed to update Windows SMTC status", e)
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        try {
            musicProperties?.comRelease()
            musicProperties = null

            displayUpdater?.comRelease()
            displayUpdater = null

            smtc?.let { s ->
                s.comCall(7, 0) // put_IsEnabled(false)
                s.comRelease()
            }
            smtc = null
        } catch (e: Throwable) {
            log?.log(Logger.Level.DEBUG, "Windows SMTC release cleanup error", e)
        }
    }

    private fun createHString(text: String): Pointer {
        val out = PointerByReference()
        val fn = combase?.getFunction("WindowsCreateString")
        fn?.invokeInt(arrayOf(WString(text), text.length, out))
        return out.value
    }

    private fun deleteHString(hstring: Pointer?) {
        if (hstring == null) return
        val fn = combase?.getFunction("WindowsDeleteString")
        fn?.invokeInt(arrayOf(hstring))
    }

    companion object {
        private const val IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS = "99FA124B-5452-4366-B967-B94F1E58E230"
        private const val IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS_INTEROP = "DDB0472D-C911-4A1F-86D9-DC3D71A95F5A"

        private const val BUTTON_PLAY = 0
        private const val BUTTON_PAUSE = 1
        private const val BUTTON_STOP = 2
        private const val BUTTON_RECORD = 3
        private const val BUTTON_FAST_FORWARD = 4
        private const val BUTTON_REWIND = 5
        private const val BUTTON_NEXT = 6
        private const val BUTTON_PREVIOUS = 7
        private const val BUTTON_CHANNEL_UP = 8
        private const val BUTTON_CHANNEL_DOWN = 9
    }
}
