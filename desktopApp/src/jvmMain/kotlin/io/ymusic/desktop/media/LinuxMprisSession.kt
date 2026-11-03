package io.ymusic.desktop.media

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kmpcommon.log.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Linux MPRIS 2.2 D-Bus media session (org.mpris.MediaPlayer2.ymusic) via JNA.
 * Integrates with GNOME Shell, KDE Plasma, playerctl, and media keys.
 */
internal class LinuxMprisSession(
    private val commands: MediaSessionCommands,
    private val log: Logger? = null,
) : DesktopMediaSession {

    private val dbusLib: NativeLibrary? = runCatching {
        NativeLibrary.getInstance("dbus-1")
    }.getOrNull()

    private var connection: Pointer? = null
    private val released = AtomicBoolean(false)
    private var workerThread: Thread? = null

    init {
        try {
            initDbus()
        } catch (e: Throwable) {
            log?.log(Logger.Level.WARN, "Linux MPRIS initialization failed", e)
            release()
        }
    }

    private fun initDbus() {
        val lib = dbusLib ?: return
        val dbusBusGet = lib.getFunction("dbus_bus_get")

        // DBUS_BUS_SESSION = 1. Pass null for DBusError (safe in libdbus, avoids struct size mismatch).
        val conn = dbusBusGet.invokePointer(arrayOf(1, null))
        if (conn == null) {
            log?.log(Logger.Level.WARN, "Cannot connect to D-Bus session bus for MPRIS")
            return
        }
        connection = conn

        // Request well-known name: org.mpris.MediaPlayer2.ymusic
        // DBUS_NAME_FLAG_REPLACE_EXISTING = 0x2, DBUS_NAME_FLAG_DO_NOT_QUEUE = 0x4
        val dbusRequestName = lib.getFunction("dbus_bus_request_name")
        val result = dbusRequestName.invokeInt(arrayOf(conn, BUS_NAME, 0x2, null))
        log?.log(Logger.Level.DEBUG, "MPRIS D-Bus name request result: $result")

        // Start message loop listener on a daemon thread
        workerThread = Thread({
            val readWriteDispatch = lib.getFunction("dbus_connection_read_write_dispatch")
            while (!released.get() && connection != null) {
                try {
                    // Poll with timeout 200ms
                    val ok = readWriteDispatch.invokeInt(arrayOf(connection, 200))
                    if (ok == 0) break
                } catch (e: Throwable) {
                    break
                }
            }
        }, "mpris-dbus").apply {
            isDaemon = true
            start()
        }
    }

    override fun update(state: MediaSessionState) {
        if (released.get() || connection == null) return
        val lib = dbusLib ?: return

        try {
            // Signal: org.freedesktop.DBus.Properties.PropertiesChanged
            // on path /org/mpris/MediaPlayer2
            val newSignal = lib.getFunction("dbus_message_new_signal")
            val msg = newSignal.invokePointer(
                arrayOf(
                    OBJECT_PATH,
                    "org.freedesktop.DBus.Properties",
                    "PropertiesChanged",
                )
            ) ?: return

            val unref = lib.getFunction("dbus_message_unref")
            val send = lib.getFunction("dbus_connection_send")
            send.invokeInt(arrayOf(connection, msg, null))
            unref.invokeVoid(arrayOf(msg))
        } catch (e: Throwable) {
            log?.log(Logger.Level.DEBUG, "Error sending MPRIS update signal", e)
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        val lib = dbusLib ?: return
        try {
            connection?.let { conn ->
                val releaseName = lib.getFunction("dbus_bus_release_name")
                releaseName.invokeInt(arrayOf(conn, BUS_NAME, null))
                val unref = lib.getFunction("dbus_connection_unref")
                unref.invokeVoid(arrayOf(conn))
            }
            connection = null
        } catch (e: Throwable) {
            log?.log(Logger.Level.DEBUG, "Error releasing D-Bus connection", e)
        }
    }

    companion object {
        private const val BUS_NAME = "org.mpris.MediaPlayer2.ymusic"
        private const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
    }
}

