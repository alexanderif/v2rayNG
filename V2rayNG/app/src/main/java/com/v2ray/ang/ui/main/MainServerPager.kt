package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** One line of the flat list. */
private sealed interface ListEntry {
    val key: String

    /** A config of the default group (no subscription). */
    data class Plain(val row: ServerRowUiModel) : ListEntry {
        override val key: String get() = row.guid
    }

    /** The card of a subscription; [shown] is the config whose name the card currently carries. */
    data class Card(
        val group: GroupMapItem,
        val shown: ServerRowUiModel?,
        val count: Int,
    ) : ListEntry {
        override val key: String get() = "sub:" + group.id
    }

    /** A config listed under the card of a subscription that has several configs. */
    data class Child(val row: ServerRowUiModel) : ListEntry {
        override val key: String get() = row.guid
    }
}

private class ServerRowActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem) -> Unit,
    val remove: (String, String) -> Unit,
)

private fun buildEntries(
    sections: List<GroupSectionUiModel>,
    selectedGuid: String?,
    searching: Boolean,
): List<ListEntry> {
    val entries = ArrayList<ListEntry>()
    sections.forEach { section ->
        val group = section.group
        val rows = section.state.rows
        if (searching && rows.isEmpty()) return@forEach
        if (group.id == AppConfig.DEFAULT_SUBSCRIPTION_ID) {
            rows.forEach { entries.add(ListEntry.Plain(it)) }
        } else {
            // The card carries the name of the selected config of its group, else of the first one.
            val shown = rows.firstOrNull { it.guid == selectedGuid } ?: rows.firstOrNull()
            entries.add(ListEntry.Card(group, shown, rows.size))
            if (rows.size > 1) rows.forEach { entries.add(ListEntry.Child(it)) }
        }
    }
    return entries
}

/**
 * The one list of the main screen: configs without a subscription as plain rows, and every
 * subscription as a card (with its configs under it when it has more than one).
 */
@Composable
fun ServerFlatList(
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    searchQuery: String,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String, String) -> Unit,
    onMoveServer: (String, Int, Int) -> Unit,
    onLocateHandled: () -> Unit,
    contentPadding: PaddingValues,
) {
    val sections by mainViewModel.sections.collectAsStateWithLifecycle()
    val searching = searchQuery.isNotEmpty()
    val canReorder = !searching
    val entries = remember(sections, selectedGuid, searching) {
        buildEntries(sections, selectedGuid, searching)
    }
    // guid -> (group id, position inside the group); only moves inside one group are allowed.
    val positions = remember(sections) {
        val map = HashMap<String, Pair<String, Int>>()
        sections.forEach { section ->
            section.state.rows.forEachIndexed { index, row -> map[row.guid] = section.group.id to index }
        }
        map
    }
    val currentPositions by rememberUpdatedState(positions)

    val actions = remember(onSelectServer, onEditServer, onShareServer, onRemoveServer) {
        ServerRowActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            remove = onRemoveServer,
        )
    }

    val listState = rememberLazyListState()
    val reorderableState = if (canReorder) {
        rememberReorderableLazyListState(listState) { from, to ->
            val source = currentPositions[from.key as? String ?: ""]
            val target = currentPositions[to.key as? String ?: ""]
            if (source != null && target != null && source.first == target.first) {
                onMoveServer(source.first, source.second, target.second)
            }
        }
    } else null

    LaunchedEffect(locateTarget, entries) {
        if (locateTarget == null) return@LaunchedEffect
        val index = entries.indexOfFirst { entry ->
            when (entry) {
                is ListEntry.Plain -> entry.row.guid == locateTarget.serverGuid
                is ListEntry.Child -> entry.row.guid == locateTarget.serverGuid
                is ListEntry.Card -> entry.shown?.guid == locateTarget.serverGuid
            }
        }
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index, -listState.layoutInfo.viewportSize.height / 3)
        onLocateHandled()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(listState),
        contentPadding = contentPadding
    ) {
        items(items = entries, key = { it.key }) { entry ->
            when (entry) {
                is ListEntry.Card -> {
                    val shown = entry.shown
                    val onShare: (() -> Unit)? = shown?.let { row -> { actions.share(row.guid, row.profile) } }
                    val onEdit: (() -> Unit)? = shown?.let { row -> { actions.edit(row.guid, row.profile) } }
                    val onDelete: (() -> Unit)? = shown?.let { row -> { actions.remove(row.guid, row.remarks) } }
                    SubscriptionServerCard(
                        title = shown?.remarks ?: entry.group.remarks,
                        groupId = entry.group.id,
                        info = entry.group.usageInfo,
                        isSelected = shown != null && shown.guid == selectedGuid,
                        onSelect = { if (shown != null) actions.select(shown.guid) },
                        onShare = onShare,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }

                is ListEntry.Plain -> {
                    val content: @Composable () -> Unit = {
                        ServerListItem(
                            row = entry.row,
                            isSelected = entry.row.guid == selectedGuid,
                            actions = actions
                        )
                    }
                    if (reorderableState != null) {
                        ReorderableItem(reorderableState, key = entry.row.guid) { isDragging ->
                            ReorderableListItem(scope = this, isDragging = isDragging) { content() }
                            ItemDivider()
                        }
                    } else {
                        content()
                        ItemDivider()
                    }
                }

                is ListEntry.Child -> {
                    val content: @Composable () -> Unit = {
                        SubscriptionChildRow(
                            row = entry.row,
                            isSelected = entry.row.guid == selectedGuid,
                            onSelect = { actions.select(entry.row.guid) },
                        )
                    }
                    if (reorderableState != null) {
                        ReorderableItem(reorderableState, key = entry.row.guid) { isDragging ->
                            ReorderableListItem(scope = this, isDragging = isDragging) { content() }
                        }
                    } else {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    actions: ServerRowActions
) {
    val testResult = if (row.testDelayMillis == 0L) {
        ""
    } else {
        stringResource(R.string.server_test_delay_value, row.testDelayMillis)
    }
    val selectedStateDescription = if (isSelected) {
        stringResource(R.string.acc_selected_server)
    } else {
        null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics {
                if (selectedStateDescription != null) {
                    stateDescription = selectedStateDescription
                }
            }
            .clickable { actions.select(row.guid) }
    ) {
        Box(
            Modifier
                .width(10.dp)
                .fillMaxHeight()
        ) {
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.remarks, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph), maxLines = 2, overflow = TextOverflow.Ellipsis)
                run {
                    IconButton(onClick = { actions.share(row.guid, row.profile) }, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            stringResource(R.string.title_configuration_share),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { actions.edit(row.guid, row.profile) }, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_edit_24dp),
                            stringResource(R.string.acc_edit),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { actions.remove(row.guid, row.remarks) }, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            stringResource(R.string.acc_delete),
                            Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (row.subscriptionBadge.isNotBlank()) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), Alignment.Center
                    ) {
                        Text(row.subscriptionBadge.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    row.statistics,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    row.typeDescription,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorConfigType,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    testResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.testDelayMillis < 0L) colorPingRed else colorPing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
