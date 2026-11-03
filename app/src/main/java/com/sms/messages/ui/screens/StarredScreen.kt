package com.sms.messages.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sms.messages.R
import com.sms.messages.ui.components.EmptyState
import com.sms.messages.ui.components.InitialAvatar
import com.sms.messages.ui.components.SimpleTopBar
import com.sms.messages.ui.viewmodel.StarredViewModel
import com.sms.messages.util.TimeFormat

/**
 * Every starred message in one list, newest first, the way Messages surfaces them under
 * "Starred". Tapping a row opens the conversation it belongs to.
 */
@Composable
fun StarredScreen(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    viewModel: StarredViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(title = stringResource(R.string.title_starred), onBack = onBack)
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.empty_starred),
                hint = stringResource(R.string.empty_starred_hint),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.message.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(item.conversationId) }
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(label = item.title, colorIndex = item.colorIndex, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = item.message.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = TimeFormat.full(item.message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.unstar(item.message) }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.action_unstar),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}
