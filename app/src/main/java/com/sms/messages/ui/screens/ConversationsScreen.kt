package com.sms.messages.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.sms.messages.R
import com.sms.messages.ui.components.AvatarChip
import com.sms.messages.ui.components.EmptyState
import com.sms.messages.ui.components.InitialAvatar
import com.sms.messages.ui.components.SearchPill
import com.sms.messages.ui.viewmodel.ConversationListItem
import com.sms.messages.ui.viewmodel.ConversationsViewModel
import com.sms.messages.util.TimeFormat

/**
 * The conversation list, laid out the way Messages does it: a title bar with an account
 * chip, a rounded search field, full-bleed rows without dividers, and an extended compose
 * button anchored bottom-end.
 */
@Composable
fun ConversationsScreen(
    onOpenConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenTemplates: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<ConversationListItem?>(null) }
    var accountMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ConversationsTopBar(onOpenAccountMenu = { accountMenu = true })
            SearchPill(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.search_conversations),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))

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
                    }
                    // Keeps the last row clear of the compose button.
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        if (accountMenu) {
            AccountMenu(
                hasArchived = archived.isNotEmpty(),
                onDismiss = { accountMenu = false },
                onArchived = { accountMenu = false; onOpenArchived() },
                onBlocked = { accountMenu = false; onOpenBlocked() },
                onScheduled = { accountMenu = false; onOpenScheduled() },
                onTemplates = { accountMenu = false; onOpenTemplates() },
                onSearchAll = { accountMenu = false; onOpenSearch() },
                onSettings = { accountMenu = false; onOpenSettings() },
            )
        }

        ComposeButton(
            label = stringResource(R.string.start_chat),
            onClick = onNewMessage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
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

@Composable
private fun ConversationsTopBar(onOpenAccountMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        AvatarChip(
            label = stringResource(R.string.app_name),
            onClick = onOpenAccountMenu,
        )
    }
}

/** The overflow behind the account chip, matching the entries Messages exposes. */
@Composable
private fun AccountMenu(
    hasArchived: Boolean,
    onDismiss: () -> Unit,
    onArchived: () -> Unit,
    onBlocked: () -> Unit,
    onScheduled: () -> Unit,
    onTemplates: () -> Unit,
    onSearchAll: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            modifier = Modifier.widthIn(min = 232.dp),
        ) {
            if (hasArchived) {
                MenuEntry(Icons.Filled.Archive, R.string.menu_archived, onArchived)
            }
            MenuEntry(Icons.Filled.Block, R.string.menu_spam_blocked, onBlocked)
            MenuEntry(Icons.Filled.Schedule, R.string.menu_scheduled, onScheduled)
            MenuEntry(Icons.AutoMirrored.Filled.TextSnippet, R.string.menu_templates, onTemplates)
            MenuEntry(Icons.Filled.Settings, R.string.menu_settings, onSettings)
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/**
 * A conversation row without a divider: the avatar leads, the name and snippet share the
 * remaining width, and the timestamp and unread badge sit on the trailing edge.
 */
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(label = item.name, colorIndex = item.colorIndex, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (item.unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.pinned) {
                    Spacer(Modifier.width(6.dp))
                    TrailingGlyph(Icons.Filled.PushPin)
                }
                if (item.muted) {
                    Spacer(Modifier.width(6.dp))
                    TrailingGlyph(Icons.Filled.NotificationsOff)
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
            Spacer(Modifier.height(3.dp))
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
                    Spacer(Modifier.width(10.dp))
                    UnreadBadge(item.unread)
                }
            }
        }
    }
}

@Composable
private fun TrailingGlyph(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A round count badge; wide enough for two digits without turning into a pill. */
@Composable
private fun UnreadBadge(count: Int) {
    val wide = count > 9
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = if (wide) 6.dp else 0.dp)
            .widthIn(min = 20.dp)
            .height(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The extended compose button: a speech bubble with a label, as Messages shows it. */
@Composable
private fun ComposeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
            MenuEntry(Icons.Filled.Archive, R.string.action_archive, onArchive)
            MenuEntry(Icons.Filled.DoneAll, R.string.action_mark_read, onMarkRead)
            MenuEntry(
                Icons.Filled.PushPin,
                if (item.pinned) R.string.action_unpin else R.string.action_pin,
                onPin,
            )
            MenuEntry(
                Icons.Filled.NotificationsOff,
                if (item.muted) R.string.action_unmute else R.string.action_mute,
                onMute,
            )
            MenuEntry(Icons.Filled.Block, R.string.action_block, onBlock)
            MenuEntry(Icons.Filled.Delete, R.string.action_delete, onDelete)
        }
    }
}

@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
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
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(label = item.name, colorIndex = item.colorIndex, size = 44.dp)
                        Spacer(Modifier.width(14.dp))
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
                        TextButton(onClick = { onOpenConversation(item.id) }) {
                            Text(stringResource(R.string.action_open))
                        }
                        TextButton(onClick = { viewModel.unarchive(item.id) }) {
                            Text(stringResource(R.string.action_restore))
                        }
                    }
                }
            }
        }
    }
}

/** A lightweight title bar: back arrow, title, and optional trailing actions. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 12.dp else 0.dp),
        )
        actions()
    }
}
