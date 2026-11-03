package com.sms.messages.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms.messages.R
import com.sms.messages.ui.components.EmptyState
import com.sms.messages.ui.components.InitialAvatar
import com.sms.messages.ui.viewmodel.ConversationListItem
import com.sms.messages.ui.viewmodel.ConversationsViewModel
import com.sms.messages.ui.viewmodel.VmFactory
import com.sms.messages.util.TimeFormat

@Composable
fun ConversationsScreen(
    onOpenConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: ConversationsViewModel = viewModel(factory = VmFactory.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val unread by viewModel.unreadTotal.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<ConversationListItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = stringResource(R.string.action_search_all))
                }
            },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (archived.isNotEmpty()) {
            TextButton(
                onClick = onOpenArchived,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.archived_count, archived.size))
            }
        }

        if (state.items.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Filled.ChatBubbleOutline,
                title = stringResource(R.string.empty_conversations),
                hint = stringResource(R.string.empty_conversations_hint),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { item ->
                    ConversationRow(
                        item = item,
                        onClick = {
                            viewModel.markRead(item.id)
                            onOpenConversation(item.id)
                        },
                        onLongClick = { menuFor = item },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    menuFor?.let { item ->
        ConversationContextMenu(
            item = item,
            onDismiss = { menuFor = null },
            onArchive = { viewModel.archive(item.id); menuFor = null },
            onMarkRead = { viewModel.markRead(item.id); menuFor = null },
            onPin = { viewModel.togglePin(item.id, item.pinned); menuFor = null },
            onMute = { viewModel.toggleMute(item.id, item.muted); menuFor = null },
            onBlock = { viewModel.block(item.address); menuFor = null },
            onDelete = { viewModel.delete(item.id); menuFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ConversationListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(label = item.name, colorIndex = item.colorIndex)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (item.unread > 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = TimeFormat.relative(
                        timestamp = item.timestamp,
                        nowLabel = stringResource(R.string.time_now),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.draft.ifBlank { item.snippet },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.draft.isNotBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (item.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = item.unread.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationContextMenu(
    item: ConversationListItem,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onMarkRead: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_archive)) },
                leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                onClick = onArchive,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_mark_read)) },
                leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                onClick = onMarkRead,
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (item.pinned) R.string.action_unpin else R.string.action_pin)) },
                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                onClick = onPin,
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (item.muted) R.string.action_unmute else R.string.action_mute)) },
                leadingIcon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                onClick = onMute,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_block)) },
                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                onClick = onBlock,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    viewModel: ConversationsViewModel = viewModel(factory = VmFactory.Factory),
) {
    val items by viewModel.archived.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.tab_archived), onBack = onBack)
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Archive,
                title = stringResource(R.string.empty_archived),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(label = item.name, colorIndex = item.colorIndex, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.snippet,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onOpenConversation(item.id) }) { Text(stringResource(R.string.action_open)) }
                        TextButton(onClick = { viewModel.unarchive(item.id) }) { Text(stringResource(R.string.action_restore)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 8.dp else 0.dp),
        )
        actions()
    }
}
