package io.ymusic.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformDefaultsTest {
    @BeforeTest
    fun setUp() {
        PlatformDefaults.resumeOnLaunchDefault = true
    }

    @AfterTest
    fun tearDown() {
        PlatformDefaults.resumeOnLaunchDefault = true
    }

    @Test
    fun defaultResumeOnLaunchIsMobileTrue() {
        assertTrue(PlatformDefaults.resumeOnLaunchDefault)
        assertEquals(true, AppSettings.resumeOnLaunch.default)
    }

    @Test
    fun desktopCanConfigureResumeOnLaunchToFalse() {
        PlatformDefaults.resumeOnLaunchDefault = false
        assertFalse(PlatformDefaults.resumeOnLaunchDefault)
        assertEquals(false, AppSettings.resumeOnLaunch.default)
    }

    @Test
    fun storeResolvesConfiguredDefaultWhenUnset() {
        val store = InMemorySettingsStore()
        PlatformDefaults.resumeOnLaunchDefault = false
        assertFalse(store.flow(AppSettings.resumeOnLaunch).value)

        PlatformDefaults.resumeOnLaunchDefault = true
        assertTrue(store.flow(AppSettings.resumeOnLaunch).value)
    }
}
