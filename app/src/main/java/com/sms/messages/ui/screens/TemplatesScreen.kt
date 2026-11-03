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
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms.messages.R
import com.sms.messages.data.TemplateEntity
import com.sms.messages.ui.components.EmptyState
import com.sms.messages.ui.viewmodel.TemplatesViewModel
import com.sms.messages.ui.viewmodel.VmFactory

@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = viewModel(factory = VmFactory.Factory),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TemplateEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.templates_title))
        if (templates.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                title = stringResource(R.string.empty_templates),
                hint = stringResource(R.string.empty_templates_hint),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(templates, key = { it.id }) { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = template.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { editing = template }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                                }
                                IconButton(onClick = { viewModel.delete(template) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = template.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    FloatingActionButton(
        onClick = { creating = true },
        modifier = Modifier.padding(16.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.template_new))
    }

    if (creating || editing != null) {
        TemplateEditorDialog(
            template = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { title, body ->
                viewModel.save(editing?.id, title, body)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    template: TemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(template?.title.orEmpty()) }
    var body by remember { mutableStateOf(template?.body.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (template == null) R.string.template_new else R.string.action_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.template_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text(stringResource(R.string.template_body_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body) },
                enabled = title.isNotBlank() && body.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun BlockedScreen(
    viewModel: com.sms.messages.ui.viewmodel.BlockedViewModel = viewModel(factory = VmFactory.Factory),
) {
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.settings_blocked))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                placeholder = { Text(stringResource(R.string.blocked_number_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    viewModel.block(number)
                    number = ""
                },
                enabled = number.isNotBlank(),
            ) { Text(stringResource(R.string.action_block)) }
        }
        Spacer(Modifier.height(8.dp))
        if (blocked.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.empty_blocked),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(blocked, key = { it.address }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.displayName ?: entry.address,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = entry.address,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.unblock(entry.address) }) {
                            Text(stringResource(R.string.action_unblock))
                        }
                    }
                }
            }
        }
    }
}
