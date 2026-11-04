package io.ymusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
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
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * Mobile layout golden screenshot regression tests.
 * Forces mobile layout (isDesktopPlatform() == false) and verifies pixel parity
 * against the feature/no-repeat baseline images.
 * Tolerance: maximum 0.2% differing pixels with per-channel RGB difference <= 10.
 */
@OptIn(ExperimentalTestApi::class)
class MobileGoldenScreenshotTest {

    @BeforeTest
    fun setup() {
        forceMobileLayoutForTest = true
    }

    @AfterTest
    fun tearDown() {
        forceMobileLayoutForTest = false
    }

    private fun compareWithGolden(name: String, testRule: androidx.compose.ui.test.ComposeUiTest) {
        val roots = testRule.onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        val rootNode = if (count > 1) roots[count - 1] else testRule.onRoot()
        val imageBitmap = rootNode.captureToImage()
        val renderedData = Image.makeFromBitmap(imageBitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
            ?: fail("Failed to encode PNG for $name")
        val renderedImg = ImageIO.read(ByteArrayInputStream(renderedData.bytes))

        val goldenStream = javaClass.getResourceAsStream("/golden/$name.png")
            ?: fail("Golden image not found for $name at /golden/$name.png")
        val goldenImg = ImageIO.read(goldenStream)

        if (renderedImg.width != goldenImg.width || renderedImg.height != goldenImg.height) {
            fail("Dimensions mismatch for $name: rendered (${renderedImg.width}x${renderedImg.height}) vs golden (${goldenImg.width}x${goldenImg.height})")
        }

        var differingPixels = 0
        val totalPixels = renderedImg.width * renderedImg.height

        for (y in 0 until renderedImg.height) {
            for (x in 0 until renderedImg.width) {
                val rgb1 = renderedImg.getRGB(x, y)
                val rgb2 = goldenImg.getRGB(x, y)
                if (rgb1 != rgb2) {
                    val r1 = (rgb1 shr 16) and 0xFF
                    val g1 = (rgb1 shr 8) and 0xFF
                    val b1 = rgb1 and 0xFF
                    val r2 = (rgb2 shr 16) and 0xFF
                    val g2 = (rgb2 shr 8) and 0xFF
                    val b2 = rgb2 and 0xFF

                    if (abs(r1 - r2) > 10 || abs(g1 - g2) > 10 || abs(b1 - b2) > 10) {
                        differingPixels++
                    }
                }
            }
        }

        val diffRatio = differingPixels.toDouble() / totalPixels
        val maxAllowedRatio = 0.002 // 0.2% tolerance for font rasterization differences across platforms
        if (diffRatio > maxAllowedRatio) {
            fail("Golden comparison failed for $name: $differingPixels differing pixels ($diffRatio > $maxAllowedRatio)")
        }
    }

    @Test
    fun verifyMobileLayoutAgainstGolden() {
        if (System.getProperty("ymusic.skip.previews") == "true") {
            return
        }
        val isHeadless = java.awt.GraphicsEnvironment.isHeadless()
        if (isHeadless) {
            println("MobileGoldenScreenshotTest: running in headless environment, skipping")
            return
        }

        Locale.setDefault(Locale.ENGLISH)
        val client = CannedHttp { request, _ -> textResponse(request, "{}", "application/json") }
        val engines = DynamicEngineManager(client, appVersion = 1, jsSource = embeddedEngineJsSource())
        val player = StubPlayer()
        val queueItems = (0 until 6).map { PlayableItem.LocalMedia("file:///music/song_$it.mp3") }
        player.queueState.value = QueueState(items = queueItems, currentIndex = 0)

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
                compareWithGolden("home_$themeName", this)
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
                compareWithGolden("player_$themeName", this)
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
                compareWithGolden("queue_$themeName", this)
            }

            // 4. Search screen
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
                compareWithGolden("search_$themeName", this)
            }

            // 5. Settings screen
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
                compareWithGolden("settings_$themeName", this)
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
                compareWithGolden("changelog_$themeName", this)
            }
        }
    }
}
