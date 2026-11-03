package com.sms.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sms.messages.R
import com.sms.messages.ui.viewmodel.ConversationListItem
import com.sms.messages.ui.viewmodel.TabItem

/** Palette offered for a custom tab, kept small so the choice stays obvious. */
private val TAB_COLORS = listOf(
    0xFF1A73E8, 0xFF188038, 0xFFD93025, 0xFFE37400, 0xFF9334E6, 0xFF00838F,
)

/**
 * Creates a tab, or edits an existing one.
 *
 * Editing also lists the conversations so they can be filed into or out of the tab, which
 * is the only way membership changes outside the per-conversation menu.
 */
@Composable
fun TabEditorDialog(
    existing: TabItem?,
    conversations: List<ConversationListItem>,
    onDismiss: () -> Unit,
    onSave: (name: String, iconIndex: Int, colorIndex: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onToggleMembership: (conversationId: Long, currentlyIn: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.label.orEmpty()) }
    var colorIndex by remember { mutableStateOf(existing?.colorIndex ?: 0) }
    var error by remember { mutableStateOf<String?>(null) }

    val requiredText = stringResource(R.string.tab_name_required)
    val editing = existing?.categoryId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editing) R.string.tab_edit_title else R.string.tab_new_title
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.tab_name_hint)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tab_icon),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TAB_COLORS.forEachIndexed { index, value ->
                        ColorDot(
                            color = Color(value),
                            selected = index == colorIndex,
                            onClick = { colorIndex = index },
                        )
                    }
                }

                if (editing && conversations.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.tab_manage_conversations),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(conversations, key = { it.id }) { item ->
                            val inTab = item.categoryIds.contains(existing?.categoryId)
                            MembershipRow(
                                label = item.name,
                                snippet = item.snippet,
                                checked = inTab,
                                onToggle = { onToggleMembership(item.id, inTab) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = requiredText
                    } else {
                        onSave(name.trim(), existing?.iconIndex ?: 0, colorIndex)
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.action_delete_tab),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MembershipRow(
    label: String,
    snippet: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Confirms deleting a tab; the conversations themselves are never touched. */
@Composable
fun DeleteTabDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_delete_title)) },
        text = { Text(stringResource(R.string.tab_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The sheet shown when a conversation is filed into a tab from its own menu. */
@Composable
fun AddToTabSheet(
    tabs: List<TabItem>,
    categoryIds: Set<Long>,
    onToggle: (categoryId: Long, currentlyIn: Boolean) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_to_tab)) },
        text = {
            Column {
                if (tabs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tab_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(tabs, key = { it.key }) { tab ->
                            val id = tab.categoryId ?: return@items
                            val inTab = id in categoryIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(id, inTab) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = inTab, onCheckedChange = { onToggle(id, inTab) })
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) { Text(stringResource(R.string.tab_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
