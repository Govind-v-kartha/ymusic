package io.ymusic.desktop

import java.io.File

internal object DesktopFileValidator {
    val ALLOWED_EXTENSIONS = setOf("mp3", "m4a", "flac", "opus", "ogg", "wav", "mp4", "webm")
    const val MAX_FILES = 100

    /**
     * Validates and returns canonical, existing regular media files matching the allowlist.
     * Enforces canonical path resolution, regular file verification, extension filtering,
     * and a sane maximum count.
     */
    fun validateMediaFiles(rawFiles: Iterable<File>): List<File> {
        return rawFiles.asSequence()
            .take(MAX_FILES)
            .mapNotNull { f -> runCatching { f.canonicalFile }.getOrNull() }
            .filter { file ->
                file.exists() && file.isFile && file.extension.lowercase() in ALLOWED_EXTENSIONS
            }
            .toList()
    }

    fun validatePath(path: String): File? {
        if (path.isBlank()) return null
        return validateMediaFiles(listOf(File(path))).firstOrNull()
    }
}
