package io.ymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.settings.AppSettings
import io.ymusic.settings.flow
import io.ymusic.settings.put
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.changelog_whats_new
import io.ymusic.ui.resources.settings_changelog
import io.ymusic.ui.resources.update_close
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.jetbrains.compose.resources.stringResource
import projectcommon.json.arrayOrNull
import projectcommon.json.int
import projectcommon.json.string

data class ChangelogEntry(
    val id: Int,
    val version: String,
    val date: String,
    val notes: List<String>,
)

object ChangelogStore {
    private const val BUNDLED_JSON = """[
  {
    "id": 1,
    "version": "v4.0.0-personal.1",
    "date": "2026-11-03",
    "notes": [
      "No-repeat window setting (1h, 2h, 3h, 4h, 6h, 12h, 24h) preventing recent song repetition",
      "Anti-spam repeat policy with fatigue decay and affinity preservation",
      "Rest this song action in playback 3-dot overflow menu (1h, 3h, 12h, 24h)",
      "Compact no-repeat chip on Discover home screen and Now Playing menus",
      "Clear heard history and clear search history actions in Settings",
      "Personal release build targeting arm64-v8a with streamlined packaging"
    ]
  }
]"""

    val entries: List<ChangelogEntry> by lazy {
        parseJson(BUNDLED_JSON)
    }

    val latestEntry: ChangelogEntry?
        get() = entries.firstOrNull()

    fun parseJson(raw: String): List<ChangelogEntry> = try {
        val root = Json.parseToJsonElement(raw).jsonArray
        root.filterIsInstance<JsonObject>().map { obj ->
            val notesList = obj.arrayOrNull("notes")?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            ChangelogEntry(
                id = obj.int("id"),
                version = obj.string("version"),
                date = obj.string("date"),
                notes = notesList,
            )
        }.sortedByDescending { it.id }
    } catch (_: Throwable) {
        emptyList()
    }
}

/** First launch after update: shows What is new dialog once, never on fresh install. */
@Composable
internal fun WhatNewDialog(graph: AppGraph) {
    val settings = graph.settings
    val lastSeen = settings.flow(AppSettings.lastSeenChangelogId).value
    val latest = ChangelogStore.latestEntry ?: return
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(latest.id) {
        if (settings.startedEmpty) {
            settings.put(AppSettings.lastSeenChangelogId, latest.id)
        } else if (latest.id > lastSeen) {
            showDialog = true
        }
    }

    if (showDialog) {
        ChangelogDialog(
            title = stringResource(Res.string.changelog_whats_new),
            entries = listOf(latest),
            onDismiss = {
                showDialog = false
                graph.scope.launch {
                    settings.put(AppSettings.lastSeenChangelogId, latest.id)
                }
            },
        )
    }
}

@Composable
internal fun ChangelogRow() {
    var open by remember { mutableStateOf(false) }
    Text(
        stringResource(Res.string.settings_changelog),
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
    )
    if (open) {
        ChangelogDialog(
            title = stringResource(Res.string.settings_changelog),
            entries = ChangelogStore.entries,
            onDismiss = { open = false },
        )
    }
}

@Composable
internal fun ChangelogDialog(
    title: String,
    entries: List<ChangelogEntry>,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    ElevatedBarDialog(
        title = title,
        onDismiss = onDismiss,
        canScrollBackward = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
        buttons = {
            DialogSecondaryButton(
                text = stringResource(Res.string.update_close),
                onClick = onDismiss,
            )
        },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entry.version,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                        )
                        Text(
                            entry.date,
                            style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                        )
                    }
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                        entry.notes.forEach { note ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "*",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignColors.Accent),
                                )
                                Text(
                                    note,
                                    style = TextStyle(fontSize = 13.sp, color = DesignColors.Ink),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = DesignColors.Divider, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
