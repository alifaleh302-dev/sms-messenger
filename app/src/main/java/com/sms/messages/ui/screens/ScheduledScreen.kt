package com.sms.messages.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms.messages.ui.components.EmptyState
import com.sms.messages.ui.viewmodel.ScheduledViewModel
import com.sms.messages.ui.viewmodel.VmFactory
import com.sms.messages.util.TimeFormat

@Composable
fun ScheduledScreen(
    viewModel: ScheduledViewModel = viewModel(factory = VmFactory.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Scheduled messages")
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Schedule,
                title = "No scheduled messages",
                hint = "Pick a date and time while composing to queue a message.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.displayName ?: item.address,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "Sends ${TimeFormat.full(item.scheduledAt)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                TextButton(onClick = { viewModel.cancel(item) }) { Text("Cancel") }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(item.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(
    onRequest: () -> Unit,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Permissions required",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onRequest) { Text("Grant permissions") }
    }
}
