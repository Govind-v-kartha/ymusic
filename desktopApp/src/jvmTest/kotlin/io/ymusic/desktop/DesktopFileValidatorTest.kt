package io.ymusic.desktop

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopFileValidatorTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File.createTempFile("validator_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun validatesValidMediaFiles() {
        val mp3 = File(tempDir, "song.mp3").apply { writeText("audio") }
        val flac = File(tempDir, "track.flac").apply { writeText("audio") }
        val txt = File(tempDir, "notes.txt").apply { writeText("text") }
        val subDir = File(tempDir, "folder.mp3").apply { mkdirs() }

        val validated = DesktopFileValidator.validateMediaFiles(listOf(mp3, flac, txt, subDir))
        assertEquals(listOf(mp3.canonicalFile, flac.canonicalFile), validated)
    }

    @Test
    fun capsAtMaxFiles() {
        val files = (0..150).map { i ->
            File(tempDir, "track_$i.mp3").apply { writeText("audio") }
        }
        val validated = DesktopFileValidator.validateMediaFiles(files)
        assertEquals(DesktopFileValidator.MAX_FILES, validated.size)
    }

    @Test
    fun rejectsNonExistentOrEmptyPaths() {
        assertNull(DesktopFileValidator.validatePath(""))
        assertNull(DesktopFileValidator.validatePath("   "))
        assertNull(DesktopFileValidator.validatePath(File(tempDir, "does_not_exist.mp3").path))
    }
}
