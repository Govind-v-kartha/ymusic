package io.ymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.settings.AppSettings
import io.ymusic.settings.flow
import io.ymusic.settings.put
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.no_repeat_chip
import io.ymusic.ui.resources.settings_no_repeat_12h
import io.ymusic.ui.resources.settings_no_repeat_1h
import io.ymusic.ui.resources.settings_no_repeat_24h
import io.ymusic.ui.resources.settings_no_repeat_2h
import io.ymusic.ui.resources.settings_no_repeat_30d
import io.ymusic.ui.resources.settings_no_repeat_3h
import io.ymusic.ui.resources.settings_no_repeat_48h
import io.ymusic.ui.resources.settings_no_repeat_4h
import io.ymusic.ui.resources.settings_no_repeat_6h
import io.ymusic.ui.resources.settings_no_repeat_7d
import io.ymusic.ui.resources.settings_no_repeat_8h
import io.ymusic.ui.resources.settings_no_repeat_hours
import io.ymusic.ui.resources.settings_no_repeat_hours_helper
import io.ymusic.ui.resources.settings_no_repeat_off
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val NoRepeatOptions: List<Pair<Int, StringResource>> = listOf(
    0 to Res.string.settings_no_repeat_off,
    1 to Res.string.settings_no_repeat_1h,
    2 to Res.string.settings_no_repeat_2h,
    4 to Res.string.settings_no_repeat_4h,
    8 to Res.string.settings_no_repeat_8h,
    24 to Res.string.settings_no_repeat_24h,
    48 to Res.string.settings_no_repeat_48h,
    168 to Res.string.settings_no_repeat_7d,
    720 to Res.string.settings_no_repeat_30d,
)

val RestOptions: List<Pair<Long, StringResource>> = listOf(
    1L * 3600 * 1000 to Res.string.settings_no_repeat_1h,
    3L * 3600 * 1000 to Res.string.settings_no_repeat_3h,
    12L * 3600 * 1000 to Res.string.settings_no_repeat_12h,
    24L * 3600 * 1000 to Res.string.settings_no_repeat_24h,
)

@Composable
fun NoRepeatDropdownMenu(
    expanded: Boolean,
    currentHours: Int,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        NoRepeatOptions.forEach { (value, labelRes) ->
            val selected = value == currentHours
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(labelRes),
                        color = if (selected) DesignColors.Accent else DesignColors.Ink,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                trailingIcon = {
                    if (selected) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = DesignColors.Accent)
                    }
                },
                onClick = {
                    onDismissRequest()
                    onSelect(value)
                },
            )
        }
    }
}

@Composable
fun NoRepeatRow(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val current by remember(graph) { graph.settings.flow(AppSettings.noRepeatHours) }.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        ) {
            Text(
                stringResource(Res.string.settings_no_repeat_hours),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            val option = NoRepeatOptions.firstOrNull { it.first == current } ?: NoRepeatOptions.first()
            Text(
                stringResource(option.second),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
            Text(
                stringResource(Res.string.settings_no_repeat_hours_helper),
                style = TextStyle(fontSize = 11.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        NoRepeatDropdownMenu(
            expanded = open,
            currentHours = current,
            onDismissRequest = { open = false },
            onSelect = { scope.launch { graph.settings.put(AppSettings.noRepeatHours, it) } },
        )
    }
}

@Composable
fun NoRepeatChip(graph: AppGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val current by remember(graph) { graph.settings.flow(AppSettings.noRepeatHours) }.collectAsState()
    var open by remember { mutableStateOf(false) }
    val option = NoRepeatOptions.firstOrNull { it.first == current } ?: NoRepeatOptions.first()
    val label = stringResource(Res.string.no_repeat_chip, stringResource(option.second))
    Box(modifier) {
        ChipTapArea({ open = true }) { indication ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .then(indication)
                    .chipPadding(leadingIcon = true),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.History,
                    null,
                    Modifier.size(16.dp).spokenAs(label),
                    tint = DesignColors.InkSoft,
                )
                Text(
                    label,
                    style = TextStyle(fontSize = ChipTextSize, fontWeight = FontWeight.Medium, color = DesignColors.Ink),
                    maxLines = 1,
                )
            }
        }
        NoRepeatDropdownMenu(
            expanded = open,
            currentHours = current,
            onDismissRequest = { open = false },
            onSelect = { scope.launch { graph.settings.put(AppSettings.noRepeatHours, it) } },
        )
    }
}
