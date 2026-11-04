package io.ymusic.desktop

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeIntegrationGuardTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File.createTempFile("ymusic_guard_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun normalRunCreatesAndCleansMarker() {
        val guard = NativeIntegrationGuard(tempDir, getEnv = { null })
        assertTrue(guard.shouldInitialize())

        var executed = false
        val result = guard.runGuarded {
            executed = true
            "ok"
        }

        assertTrue(executed)
        assertEquals("ok", result)
        assertFalse(File(tempDir, ".native_crash_marker").exists(), "Marker should be cleaned up after successful init")
    }

    @Test
    fun crashMarkerFromPreviousRunPreventsInitialization() {
        val marker = File(tempDir, ".native_crash_marker")
        marker.writeText("crashed\n")

        val guard = NativeIntegrationGuard(tempDir, getEnv = { null })
        assertFalse(guard.shouldInitialize(), "Should not initialize when crash marker exists")

        val result = guard.runGuarded { "should_not_run" }
        assertNull(result)
        assertFalse(marker.exists(), "Stale marker should be deleted after detecting crash")
    }

    @Test
    fun envVarDisablesNativeIntegrations() {
        val guard = NativeIntegrationGuard(tempDir, getEnv = { key ->
            if (key == "YMUSIC_DISABLE_NATIVE_INTEGRATIONS") "1" else null
        })

        assertFalse(guard.shouldInitialize(), "Environment variable must disable native integrations")
        val result = guard.runGuarded { "should_not_run" }
        assertNull(result)
    }
}
