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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms.messages.R
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.ui.components.InitialAvatar
import com.sms.messages.ui.components.MessageBubble
import com.sms.messages.ui.viewmodel.ChatViewModel
import com.sms.messages.ui.viewmodel.VmFactory
import com.sms.messages.util.TimeFormat

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = VmFactory.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MessageEntity?>(null) }
    val sendFailed by viewModel.sendFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailedText = stringResource(R.string.error_send_failed)
    val todayLabel = stringResource(R.string.time_today)
    val yesterdayLabel = stringResource(R.string.time_yesterday)

    LaunchedEffect(sendFailed) {
        if (sendFailed) {
            snackbarHostState.showSnackbar(sendFailedText)
            viewModel.clearSendFailure()
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                InitialAvatar(label = state.title, colorIndex = state.colorIndex, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.blocked) {
                        Text(
                            text = stringResource(R.string.state_blocked),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${state.address}"))
                    runCatching { context.startActivity(intent) }
                }) {
                    Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.action_call))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (state.archived) R.string.action_unarchive else R.string.action_archive)) },
                            leadingIcon = {
                                Icon(
                                    if (state.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    contentDescription = null,
                                )
                            },
                            onClick = { viewModel.toggleArchive(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (state.pinned) R.string.action_unpin else R.string.action_pin)) },
                            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                            onClick = { viewModel.togglePin(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (state.muted) R.string.action_unmute else R.string.action_mute)) },
                            leadingIcon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                            onClick = { viewModel.toggleMute(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (state.blocked) R.string.action_unblock else R.string.action_block)) },
                            leadingIcon = {
                                Icon(
                                    if (state.blocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                if (state.blocked) viewModel.unblock() else viewModel.block()
                                menuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete_conversation)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                viewModel.deleteConversation()
                                menuOpen = false
                                onBack()
                            },
                        )
                    }
                }
            }
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
            state.messages.forEach { message ->
                val day = TimeFormat.dayLabel(
                    timestamp = message.timestamp,
                    todayLabel = todayLabel,
                    yesterdayLabel = yesterdayLabel,
                )
                if (day != lastDay) {
                    lastDay = day
                    item(key = "day-$day") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item(key = message.id) {
                    MessageBubble(
                        message = message,
                        onLongPress = { selected = message },
                        onRetry = { viewModel.retry(message.id) },
                    )
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text(stringResource(R.string.message_hint)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.send() },
                    enabled = state.draft.isNotBlank() && !state.blocked,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
                }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    selected?.let { message ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(TimeFormat.full(message.timestamp)) },
            text = {
                Column {
                    Text(message.body)
                    if (message.status == MessageStatus.FAILED) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.message_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(message.body))
                    selected = null
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy))
                }
            },
            dismissButton = {
                Row {
                    if (message.status == MessageStatus.FAILED) {
                        TextButton(onClick = { viewModel.retry(message.id); selected = null }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    TextButton(onClick = { viewModel.deleteMessage(message.id); selected = null }) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            },
        )
    }
}
