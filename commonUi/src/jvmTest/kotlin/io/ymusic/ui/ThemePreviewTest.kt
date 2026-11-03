package io.ymusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.ymusic.dynamicengine.DynamicEngineManager
import io.ymusic.dynamicengine.embeddedEngineJsSource
import io.ymusic.personal.PersonalDarkPalette
import io.ymusic.personal.PersonalLightPalette
import io.ymusic.player.PlayableItem
import io.ymusic.player.QueueState
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.Test

/**
 * Renders Compose previews of key app screens in light and dark personal theme to branding/preview/.
 */
@OptIn(ExperimentalTestApi::class)
class ThemePreviewTest {

    private fun savePreview(name: String, testRule: androidx.compose.ui.test.ComposeUiTest) {
        val roots = testRule.onAllNodes(androidx.compose.ui.test.isRoot())
        val count = roots.fetchSemanticsNodes().size
        val rootNode = if (count > 1) roots[count - 1] else testRule.onRoot()
        val imageBitmap = rootNode.captureToImage()
        val data = Image.makeFromBitmap(imageBitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
        val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").exists() } ?: File(".")
        val file = File(repoRoot, "branding/preview/$name.png")
        file.parentFile?.mkdirs()
        file.writeBytes(checkNotNull(data?.bytes) { "Failed to encode PNG for $name" })
        println("Generated preview: ${file.path} (${file.length()} bytes)")
    }

    @Test
    fun generateThemePreviews() {
        Locale.setDefault(Locale.ENGLISH)
        val client = CannedHttp { request, _ -> textResponse(request, "{}", "application/json") }
        val engines = DynamicEngineManager(client, appVersion = 1, jsSource = embeddedEngineJsSource())
        val player = StubPlayer()
        val queueItems = (0 until 6).map { PlayableItem.LocalMedia("file:///music/song_$it.mp3") }
        player.queueState.value = QueueState(items = queueItems, currentIndex = 0)

        val outDir = File("branding/preview")
        outDir.mkdirs()

        val themes = listOf(
            "light" to PersonalLightPalette,
            "dark" to PersonalDarkPalette,
        )

        for ((themeName, palette) in themes) {
            // 1. Home / App screen
            runComposeUiTest {
                setContent {
                    val graph = AppGraph(engines = engines, player = player, playerSurface = {})
                    YMusicTheme(palette) {
                        Box(Modifier.requiredSize(400.dp, 800.dp)) {
                            App(graph)
                        }
                    }
                }
                waitForIdle()
                savePreview("home_$themeName", this)
            }

            // 2. Player screen
            runComposeUiTest {
                setContent {
                    val graph = AppGraph(engines = engines, player = player, playerSurface = {})
                    val coordinator = PlaybackCoordinator(graph, rememberCoroutineScope())
                    YMusicTheme(palette) {
                        Box(Modifier.requiredSize(400.dp, 800.dp)) {
                            NowPlayingScreen(graph, coordinator, onClose = {})
                        }
                    }
                }
                waitForIdle()
                savePreview("player_$themeName", this)
            }

            // 3. Queue screen
            runComposeUiTest {
                setContent {
                    val graph = AppGraph(engines = engines, player = player, playerSurface = {})
                    val coordinator = PlaybackCoordinator(graph, rememberCoroutineScope())
                    YMusicTheme(palette) {
                        Box(Modifier.requiredSize(400.dp, 800.dp)) {
                            NowPlayingScreen(graph, coordinator, onClose = {})
                        }
                    }
                }
                waitForIdle()
                savePreview("queue_$themeName", this)
            }

            // 4. Settings screen
            runComposeUiTest {
                setContent {
                    val graph = AppGraph(engines = engines, player = player, playerSurface = {})
                    YMusicTheme(palette) {
                        Box(Modifier.requiredSize(400.dp, 800.dp)) {
                            SettingsScreen(graph)
                        }
                    }
                }
                waitForIdle()
                savePreview("settings_$themeName", this)
            }

            // 5. Search screen
            runComposeUiTest {
                setContent {
                    val graph = AppGraph(engines = engines, player = player, playerSurface = {})
                    val coordinator = PlaybackCoordinator(graph, rememberCoroutineScope())
                    YMusicTheme(palette) {
                        Box(Modifier.requiredSize(400.dp, 800.dp)) {
                            SearchScreen(graph, coordinator, onBack = {})
                        }
                    }
                }
                waitForIdle()
                savePreview("search_$themeName", this)
            }

            // 6. Changelog dialog
            runComposeUiTest {
                setContent {
                    val entries = ChangelogStore.entries
                    YMusicTheme(palette) {
                        Box(Modifier.requiredSize(400.dp, 800.dp)) {
                            ChangelogDialog(title = "What's New", entries = entries, onDismiss = {})
                        }
                    }
                }
                waitForIdle()
                savePreview("changelog_$themeName", this)
            }
        }
    }
}
