package com.sms.messages.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms.messages.BuildConfig
import com.sms.messages.data.AppLanguage
import com.sms.messages.data.ThemeMode
import com.sms.messages.ui.viewmodel.SettingsViewModel
import com.sms.messages.ui.viewmodel.VmFactory
import com.sms.messages.util.DefaultSmsRole

@Composable
fun SettingsScreen(
    onOpenBlocked: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = VmFactory.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isDefault by viewModel.isDefaultSms.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { TopBar(title = "Settings") }

        item {
            SettingsCard(title = "Default SMS app") {
                Text(
                    text = if (isDefault) {
                        "This app is the default SMS app"
                    } else {
                        "Required to send and receive messages with this app"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isDefault) {
                    TextButton(onClick = { DefaultSmsRole.request(viewModel.getApplication()) }) {
                        Text("Set as default")
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Appearance") {
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> "System default"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        selected = settings.themeMode == mode,
                        onSelect = { viewModel.setTheme(mode) },
                    )
                }
            }
        }

        item {
            SettingsCard(title = "Language") {
                AppLanguage.entries.forEach { language ->
                    ChoiceRow(
                        label = when (language) {
                            AppLanguage.SYSTEM -> "System default"
                            AppLanguage.ENGLISH -> "English"
                            AppLanguage.ARABIC -> "العربية"
                        },
                        selected = settings.language == language,
                        onSelect = { viewModel.setLanguage(language) },
                    )
                }
            }
        }

        item {
            SettingsCard(title = "Notifications") {
                ToggleRow(
                    label = "Show notifications",
                    checked = settings.notificationsEnabled,
                    onChange = viewModel::setNotifications,
                )
                ToggleRow(
                    label = "Vibrate",
                    checked = settings.vibrate,
                    onChange = viewModel::setVibrate,
                )
                ToggleRow(
                    label = "Notification sound",
                    checked = settings.soundEnabled,
                    onChange = viewModel::setSound,
                )
            }
        }

        item {
            SettingsCard(title = "Sending") {
                ToggleRow(
                    label = "Request delivery reports",
                    checked = settings.deliveryReports,
                    onChange = viewModel::setDeliveryReports,
                )
                ToggleRow(
                    label = "Auto retry failed messages",
                    checked = settings.autoRetry,
                    onChange = viewModel::setAutoRetry,
                )
            }
        }

        item {
            var signature by remember { mutableStateOf(settings.signature) }
            LaunchedEffect(settings.signature) {
                if (signature != settings.signature) signature = settings.signature
            }
            SettingsCard(title = "Signature") {
                OutlinedTextField(
                    value = signature,
                    onValueChange = { signature = it },
                    placeholder = { Text("Appended to every outgoing message") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                TextButton(onClick = { viewModel.setSignature(signature) }) { Text("Save") }
            }
        }

        item {
            SettingsCard(title = "Blocked numbers") {
                Text(
                    text = "Messages from blocked numbers are discarded and never stored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenBlocked) { Text("Manage blocked numbers") }
            }
        }

        item {
            SettingsCard(title = "History") {
                Text(
                    text = "Import messages stored by the system before this app became default.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::importFromTelephony) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Import messages")
                }
            }
        }

        item {
            SettingsCard(title = "About") {
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "A complete SMS client: conversations, delivery reports, " +
                        "scheduled messages, templates, blocking and search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    message?.let {
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::clearMessage) { Text("OK") }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.RadioButton(selected = selected, onClick = onSelect)
    }
}
