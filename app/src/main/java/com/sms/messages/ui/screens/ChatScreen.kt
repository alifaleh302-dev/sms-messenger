package com.sms.messages.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.delay
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.sms.messages.R
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import com.sms.messages.ui.components.ChatComposer
import com.sms.messages.ui.components.DayDivider
import com.sms.messages.ui.components.InitialAvatar
import com.sms.messages.ui.components.MessageActionSheet
import com.sms.messages.ui.components.MessageBubble
import com.sms.messages.ui.components.MessageDetailsDialog
import com.sms.messages.ui.components.SelectionTopBar
import com.sms.messages.ui.viewmodel.ChatViewModel
import com.sms.messages.util.TimeFormat
import com.sms.messages.util.VerificationCode
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MessageEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<MessageEntity?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val sendFailed by viewModel.sendFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailedText = stringResource(R.string.error_send_failed)
    val todayLabel = stringResource(R.string.time_today)
    val yesterdayLabel = stringResource(R.string.time_yesterday)
    val copiedText = stringResource(R.string.toast_copied)
    val codeCopiedText = stringResource(R.string.toast_code_copied)
    val starredText = stringResource(R.string.toast_starred)
    val unstarredText = stringResource(R.string.toast_unstarred)
    val deletedText = stringResource(R.string.toast_deleted)
    val trashedText = stringResource(R.string.toast_trashed)
    val nothingToShareText = stringResource(R.string.toast_nothing_to_share)
    val undoMessageText = stringResource(R.string.undo_send_message)
    val undoLabel = stringResource(R.string.action_undo)
    val sendCancelledText = stringResource(R.string.toast_send_cancelled)

    LaunchedEffect(sendFailed) {
        if (sendFailed) {
            snackbarHostState.showSnackbar(sendFailedText)
            viewModel.clearSendFailure()
        }
    }

    // The undo bar runs its own countdown so it disappears exactly when the hold ends,
    // rather than sitting there after the message has already gone out.
    val pendingSendId = state.pendingSendId
    val undoSeconds = state.settings.undoSendSeconds
    LaunchedEffect(pendingSendId, undoSeconds) {
        if (pendingSendId == null) return@LaunchedEffect
        // Indefinite: the bar is dismissed by the countdown below, not by a fixed timer,
        // so it stays up for exactly as long as the message is still recallable.
        val result = snackbarHostState.showSnackbar(
            message = undoMessageText,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoPendingSend()
            snackbarHostState.showSnackbar(sendCancelledText)
        } else {
            viewModel.clearPendingSend()
        }
    }
    // Dismisses the bar when the hold expires, so the countdown drives the UI, not a timer
    // that could drift away from the actual send.
    LaunchedEffect(pendingSendId) {
        if (pendingSendId == null) return@LaunchedEffect
        delay(undoSeconds * 1000L)
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && !state.inSelectionMode) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // Back closes selection first, then leaves the screen, matching Messages.
    androidx.activity.compose.BackHandler(enabled = state.inSelectionMode) {
        viewModel.clearSelection()
    }

    fun toast(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun copy(text: String, label: String = copiedText) {
        clipboard.setText(AnnotatedString(text))
        toast(label)
    }

    fun share(text: String) {
        if (text.isBlank()) {
            toast(nothingToShareText)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    count = state.selectedIds.size,
                    allSelected = state.selectedIds.size == state.messages.size,
                    onClear = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onCopy = {
                        val text = viewModel.selectedText()
                        copy(text)
                        viewModel.clearSelection()
                    },
                    onForward = {
                        val text = viewModel.selectedText()
                        viewModel.clearSelection()
                        share(text)
                    },
                    onShare = {
                        val text = viewModel.selectedText()
                        viewModel.clearSelection()
                        share(text)
                    },
                    onStar = {
                        viewModel.starSelected()
                        toast(starredText)
                    },
                    onDelete = { confirmBulkDelete = true },
                )
            } else {
                ChatHeader(
                    title = state.title,
                    colorIndex = state.colorIndex,
                    blocked = state.blocked,
                    archived = state.archived,
                    pinned = state.pinned,
                    muted = state.muted,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onBack = onBack,
                    onCall = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${state.address}"))
                        runCatching { context.startActivity(intent) }
                    },
                    onToggleArchive = viewModel::toggleArchive,
                    onTogglePin = viewModel::togglePin,
                    onToggleMute = viewModel::toggleMute,
                    onToggleBlock = { if (state.blocked) viewModel.unblock() else viewModel.block() },
                    onTrash = { menuOpen = false; confirmTrash = true },
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                var lastDay = ""
                state.messages.forEachIndexed { index, message ->
                    val day = TimeFormat.dayLabel(
                        timestamp = message.timestamp,
                        todayLabel = todayLabel,
                        yesterdayLabel = yesterdayLabel,
                    )
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "day-$day") {
                            DayDivider(label = day)
                        }
                    }
                    item(key = message.id) {
                        val next = state.messages.getOrNull(index + 1)
                        val sameSenderNext = next != null &&
                            (next.type == MessageType.INBOX) == (message.type == MessageType.INBOX)
                        MessageBubble(
                            message = message,
                            onLongPress = { selected = message },
                            onRetry = { viewModel.retry(message.id) },
                            showTail = !sameSenderNext,
                            reactions = state.reactions[message.id].orEmpty(),
                            quoted = message.replyToId?.let { id ->
                                state.messages.firstOrNull { it.id == id }
                            },
                            selectionMode = state.inSelectionMode,
                            selected = message.id in state.selectedIds,
                            onToggleSelect = { viewModel.toggleSelection(message.id) },
                        )
                    }
                }
            }

            ChatComposer(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                onSend = { viewModel.send() },
                enabled = !state.blocked,
                replyPreview = state.replyTo?.body,
                onCancelReply = viewModel::cancelReply,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    // ------------------------------------------------------------- dialogs

    selected?.let { message ->
        MessageActionSheet(
            message = message,
            reactions = state.reactions[message.id].orEmpty(),
            hasSelection = state.inSelectionMode,
            onDismiss = { selected = null },
            onReact = { emoji ->
                viewModel.toggleReaction(message.id, emoji)
                selected = null
            },
            onReply = { viewModel.startReply(message); selected = null },
            onForward = { share(message.body); selected = null },
            onCopy = { copy(message.body); selected = null },
            onShare = { share(message.body); selected = null },
            onToggleStar = {
                viewModel.toggleStar(message)
                toast(if (message.starred) unstarredText else starredText)
                selected = null
            },
            onDelete = { pendingDelete = message; selected = null },
            onSelectMore = {
                viewModel.toggleSelection(message.id)
                selected = null
            },
            onInfo = { viewModel.showDetails(message); selected = null },
            onCopyCode = VerificationCode.extract(message.body)?.let { code ->
                { copy(code, codeCopiedText); selected = null }
            },
        )
    }

    state.detailsFor?.let { message ->
        MessageDetailsDialog(
            message = message,
            conversationTitle = state.title,
            onDismiss = viewModel::hideDetails,
        )
    }

    pendingDelete?.let { message ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_messages_title),
            body = pluralStringResource(R.plurals.dialog_delete_messages_body, 1, 1),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deleteMessage(message.id)
                toast(deletedText)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (confirmBulkDelete) {
        val count = state.selectedIds.size
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_messages_title),
            body = pluralStringResource(R.plurals.dialog_delete_messages_body, count, count),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deleteSelected()
                toast(deletedText)
                confirmBulkDelete = false
            },
            onDismiss = { confirmBulkDelete = false },
        )
    }

    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_conversation_title),
            body = stringResource(R.string.dialog_delete_conversation_body),
            confirmLabel = stringResource(R.string.action_trash),
            onConfirm = {
                confirmTrash = false
                viewModel.trashConversation()
                toast(trashedText)
                onBack()
            },
            onDismiss = { confirmTrash = false },
        )
    }
}

@Composable
private fun ChatHeader(
    title: String,
    colorIndex: Int,
    blocked: Boolean,
    archived: Boolean,
    pinned: Boolean,
    muted: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onToggleArchive: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onTrash: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            InitialAvatar(label = title, colorIndex = colorIndex, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (blocked) {
                    Text(
                        text = stringResource(R.string.state_blocked),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onCall) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.action_call))
            }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpenChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (archived) R.string.action_unarchive else R.string.action_archive
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = null,
                            )
                        },
                        onClick = { onToggleArchive(); onMenuOpenChange(false) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (pinned) R.string.action_unpin else R.string.action_pin))
                        },
                        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                        onClick = { onTogglePin(); onMenuOpenChange(false) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (muted) R.string.action_unmute else R.string.action_mute))
                        },
                        leadingIcon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                        onClick = { onToggleMute(); onMenuOpenChange(false) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (blocked) R.string.action_unblock else R.string.action_block))
                        },
                        leadingIcon = {
                            Icon(
                                if (blocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                                contentDescription = null,
                            )
                        },
                        onClick = { onToggleBlock(); onMenuOpenChange(false) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_trash)) },
                        leadingIcon = {
                            Icon(Icons.Filled.RestoreFromTrash, contentDescription = null)
                        },
                        onClick = onTrash,
                    )
                }
            }
        }
    }
}

/** A confirm/cancel dialog shared by the delete flows. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
