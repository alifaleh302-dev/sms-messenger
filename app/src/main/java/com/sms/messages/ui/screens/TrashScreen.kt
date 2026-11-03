package com.sms.messages.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sms.messages.R
import com.sms.messages.ui.components.EmptyState
import com.sms.messages.ui.components.InitialAvatar
import com.sms.messages.ui.components.SimpleTopBar
import com.sms.messages.ui.viewmodel.TrashItem
import com.sms.messages.ui.viewmodel.TrashViewModel

/**
 * Trash, matching Messages: deleted conversations stay recoverable for 30 days, each row
 * showing how long it has left, with restore and delete-forever actions.
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf<TrashItem?>(null) }

    LaunchedEffect(Unit) { viewModel.purgeExpired() }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = stringResource(R.string.title_trash),
            onBack = onBack,
        ) {
            if (items.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) {
                    Text(stringResource(R.string.action_delete_forever))
                }
            }
        }

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.empty_trash),
                hint = stringResource(R.string.empty_trash_hint),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(label = item.title, colorIndex = item.colorIndex, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (item.daysLeft <= 0) {
                                    stringResource(R.string.trash_expires_today)
                                } else {
                                    pluralStringResource(R.plurals.trash_days_left, item.daysLeft, item.daysLeft)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { viewModel.restore(item.id) }) {
                            Icon(
                                imageVector = Icons.Filled.RestoreFromTrash,
                                contentDescription = stringResource(R.string.action_restore_conversation),
                            )
                        }
                        IconButton(onClick = { confirmPurge = item }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = stringResource(R.string.action_delete_forever),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }

    confirmPurge?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_forever_title),
            body = stringResource(R.string.dialog_delete_forever_body),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = {
                viewModel.deleteForever(item.id)
                confirmPurge = null
            },
            onDismiss = { confirmPurge = null },
        )
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_forever_title),
            body = stringResource(R.string.dialog_delete_forever_body),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = {
                viewModel.emptyTrash()
                confirmEmpty = false
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}
