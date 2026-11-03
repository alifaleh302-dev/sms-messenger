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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms.messages.BuildConfig
import com.sms.messages.R
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
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { TopBar(title = stringResource(R.string.settings_title)) }

        item {
            SettingsCard(title = stringResource(R.string.settings_default_sms)) {
                Text(
                    text = stringResource(
                        if (isDefault) R.string.settings_is_default
                        else R.string.settings_default_sms_summary
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isDefault) {
                    TextButton(onClick = { DefaultSmsRole.request(context) }) {
                        Text(stringResource(R.string.settings_set_default))
                    }
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_theme)) {
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            }
                        ),
                        selected = settings.themeMode == mode,
                        onSelect = { viewModel.setTheme(mode) },
                    )
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_language)) {
                AppLanguage.entries.forEach { language ->
                    ChoiceRow(
                        label = when (language) {
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
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
            SettingsCard(title = stringResource(R.string.settings_notifications)) {
                Text(
                    text = stringResource(R.string.settings_notifications_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_notifications),
                    checked = settings.notificationsEnabled,
                    onChange = viewModel::setNotifications,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_vibrate),
                    checked = settings.vibrate,
                    onChange = viewModel::setVibrate,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_sound),
                    checked = settings.soundEnabled,
                    onChange = viewModel::setSound,
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_sending)) {
                Text(
                    text = stringResource(R.string.settings_delivery_reports_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_delivery_reports),
                    checked = settings.deliveryReports,
                    onChange = viewModel::setDeliveryReports,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_auto_retry),
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
            SettingsCard(title = stringResource(R.string.settings_signature)) {
                OutlinedTextField(
                    value = signature,
                    onValueChange = { signature = it },
                    placeholder = { Text(stringResource(R.string.settings_signature_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                TextButton(onClick = { viewModel.setSignature(signature) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_blocked)) {
                Text(
                    text = stringResource(R.string.blocked_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenBlocked) { Text(stringResource(R.string.action_manage_blocked)) }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_history)) {
                Text(
                    text = stringResource(R.string.settings_history_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::importFromTelephony) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_import))
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_about)) {
                Text(
                    text = stringResource(R.string.settings_version) + " " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_about_summary),
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
                TextButton(onClick = viewModel::clearMessage) { Text(stringResource(android.R.string.ok)) }
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
