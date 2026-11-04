package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.player.isPlaying
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.section_discover
import io.ymusic.ui.resources.section_downloads
import io.ymusic.ui.resources.section_library
import io.ymusic.ui.resources.section_play_again
import io.ymusic.ui.resources.section_settings
import io.ymusic.ui.resources.section_yt_music
import io.ymusic.ui.resources.ymusic_logo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Left sidebar navigation rail for desktop viewports (72dp wide).
 * Replaces the mobile bottom navigation bar and slide-in drawer with a persistent rail.
 */
@Composable
internal fun DesktopNavRail(
    currentSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        RailItem(AppSection.Discover, Icons.Outlined.Explore, Res.string.section_discover),
        RailItem(AppSection.Library, Icons.Outlined.VideoLibrary, Res.string.section_library),
        RailItem(AppSection.YtMusic, Icons.Outlined.LibraryMusic, Res.string.section_yt_music),
        RailItem(AppSection.PlayAgain, Icons.Outlined.History, Res.string.section_play_again),
        RailItem(AppSection.Downloads, Icons.Outlined.Download, Res.string.section_downloads),
    )

    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(DesignColors.SurfaceDim)
            .border(width = 1.dp, color = DesignColors.OutlineSoft)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App Logo
        Icon(
            painter = painterResource(Res.drawable.ymusic_logo),
            contentDescription = "YMusic",
            tint = DesignColors.Accent,
            modifier = Modifier
                .size(36.dp)
                .clickable { onSelectSection(AppSection.Discover) }
                .padding(4.dp),
        )

        Spacer(Modifier.height(16.dp))

        // Main Nav Items
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in items) {
                val selected = currentSection == item.section
                NavRailButton(
                    icon = item.icon,
                    label = stringResource(item.label),
                    selected = selected,
                    onClick = { onSelectSection(item.section) },
                )
            }
        }

        // Settings Button at bottom
        NavRailButton(
            icon = Icons.Outlined.Settings,
            label = stringResource(Res.string.section_settings),
            selected = currentSection == AppSection.Settings,
            onClick = { onSelectSection(AppSection.Settings) },
        )
    }
}

private data class RailItem(
    val section: AppSection,
    val icon: ImageVector,
    val label: org.jetbrains.compose.resources.StringResource,
)

@Composable
private fun NavRailButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) DesignColors.Accent.copy(alpha = 0.16f) else Color.Transparent
    val tint = if (selected) DesignColors.Accent else DesignColors.InkSecondary

    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Persistent right-side Now Playing panel for desktop screens (320-360dp wide).
 * Allows content navigation in the center pane while keeping playback visible alongside.
 */
@Composable
internal fun DesktopNowPlayingSidePanel(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue by coordinator.player.queue.collectAsState()
    val progress by coordinator.player.progress.collectAsState()
    val status by coordinator.player.status.collectAsState()
    val config by coordinator.player.config.collectAsState()
    val currentItem = queue.currentItem

    Column(
        modifier = modifier
            .width(340.dp)
            .fillMaxHeight()
            .background(DesignColors.SurfaceDim)
            .border(width = 1.dp, color = DesignColors.OutlineSoft)
            .padding(14.dp),
    ) {
        // Panel Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Now Playing",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DesignColors.Ink,
            )
            ActionIcon(
                icon = Icons.Default.Close,
                contentDescription = "Close panel",
                onClick = onClose,
                tint = DesignColors.InkSecondary,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Artwork Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (currentItem != null) {
                AudioOnlyCover(currentItem.displayThumbnailUrl, Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.height(12.dp))

        // Track Info
        Text(
            text = currentItem?.displayTitle ?: "Not playing",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DesignColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = currentItem?.displaySubtitle ?: "",
            fontSize = 12.sp,
            color = DesignColors.InkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(10.dp))

        // Progress Bar
        val duration = progress.durationMs.takeIf { it > 0 } ?: 1L
        val fraction = (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        val bufferedFraction = (progress.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)
        SlimSeekBar(
            fraction = fraction,
            enabled = progress.seekable && duration > 1,
            onSeek = { f -> coordinator.player.seekTo((f * duration).toLong()) },
            trackColor = DesignColors.OutlineSoft,
            bufferedFraction = bufferedFraction,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // Transport Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                onClick = { coordinator.player.skipPrev() },
                modifier = Modifier.size(32.dp),
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DesignColors.Accent)
                    .clickable { coordinator.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (status.state.isPlaying()) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (status.state.isPlaying()) "Pause" else "Play",
                    tint = DesignColors.OnAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
            ActionIcon(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next",
                onClick = { coordinator.player.skipNext() },
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Up Next Header & Mini Queue
        Text(
            text = "Queue (${queue.items.size})",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DesignColors.InkSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(queue.items) { index, item ->
                val isCurrent = index == queue.currentIndex
                val rowBg = if (isCurrent) DesignColors.Accent.copy(alpha = 0.12f) else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(rowBg)
                        .clickable { coordinator.player.playAt(index) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 11.sp,
                        color = if (isCurrent) DesignColors.Accent else DesignColors.InkSecondary,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        text = item.displayTitle,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent) DesignColors.Accent else DesignColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
